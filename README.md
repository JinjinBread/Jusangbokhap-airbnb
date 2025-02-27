# ☁ 구름비앤비

## 서비스 소개
### 숙박 업소 예약 시스템

### 메인페이지
![Image](/docs/images/service/main.png)

### 숙소 검색
![Image](/docs/images/service/accommodation_list.png)

![Image](/docs/images/service/accommodation_detail.png)

### 지도로 보기
![Image](/docs/images/service/accommodation_map.png)


---

## 좌표 기반 숙소 조회 성능 최적화 과정 (지도로 보기)
### 성능 이슈

- 좌표 Index(GIST Index) 만 생성하면 조회 성능이 빨라질 것을 예상했으나, 응답 속도가 비슷함

**기존 조회 쿼리문**
```sql
SELECT a.* 
FROM accommodation a
JOIN accommodation_address ad ON a.accommodation_address_id = ad.accommodation_address_id
WHERE ST_DWithin(ad.coordinate, ST_SetSRID(ST_MakePoint(경도, 위도), 4326), 반경(m), true);
```

**기존 쿼리 실행 계획**

![Image](/docs/images/optimization/before_optimization_execution_time.png)
*숙소 데이터 10,000개*

- 좌표와 반경을 기반으로 숙소 단건 조회 쿼리 수행 시, 1820.940 ms (약 1.8초) 의 시간이 소요되는 것을 볼 수 있다. (API 를 통한다면 더 오래 걸릴 것임.)
- 일반적인 웹 서비스에서의 서버 평균 응답 시간은 200ms ~ 500ms 이다. 즉, 응답 시간을 줄일 필요가 있었다. (특히나 지도는 자주 움직이기 때문에 더 빠른 응답시간이 필요하다.)
- 쿼리 실행 계획을 자세히 보면, 숙소 주소와 숙소가 NL Join 이 우선 수행된 후에 필터링이 진행되고 있다.
  - 필터링 이후 삭제되는 행이 약 1762만 개이다.

![Gif](/docs/images/optimization/service_before_optimization.gif)
- 자주 변경되는 좌표 및 반경 값으로 많은 조회 요청이 옴 → 조회 성능이 떨어져, 조회 요청이 쌓임 → 숙소 데이터가 조회되지 않음


### 해결 방안 모색
- NL Join 의 문제일까 싶어, NL Join 과 Merge Join 을 막고 Hash Join 을 강제 해보기도 했다.
![Image](/docs/images/optimization/hash_join_query_execution_time.png)
  - 확실히 쿼리 수행 시간이 단축되는 것을 볼 수 있었다.
  - 그러나, Hash Join 을 강제하는 건 일시적으로만 가능하지, 해결책이 아니라고 생각했다.
- 현재 필터링 이후 삭제되는 데이터가 매우 많다.
  - 우선적으로 필터링을 한 후 조인을 하면 더 효율적일 것 같다고 생각했다.

### 쿼리문 변경
```sql
SELECT a.*
FROM accommodation a
JOIN (SELECT accommodation_address_id
FROM accommodation_address
WHERE ST_DWithin(coordinate, ST_SetSRID(ST_MakePoint(경도, 위도), 4326), 반경(°))) ad
ON a.accommodation_address_id = ad.accommodation_address_id;
```
- 서브 쿼리를 이용하여 좌표 필터링이 우선되게 변경해보았다.  
(+ ST_DWithin(geom, distance(°)) 가 공간 인덱스를 더 효율적으로 사용)
![Image](/docs/images/optimization/after_optimization_execution_time.png)
- 좌표로 먼저 필터링을 한 후, Index Scan 을 활용해 조인이 진행됐다.
- 그 결과, 쿼리 수행 시간이 0.347 ms 로 대폭 감소한 것을 볼 수 있다.
- 99.98% 개선되었다.

![Gif](/docs/images/optimization/service_after_optimization.gif)