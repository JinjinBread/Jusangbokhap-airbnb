package jsbh.Jusangbokhap.api.accommodation.service;


import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import jsbh.Jusangbokhap.api.accommodation.dto.AccommodationCoordSearchRequest;
import jsbh.Jusangbokhap.api.accommodation.dto.AccommodationCoordSearchResponse;
import jsbh.Jusangbokhap.api.accommodation.dto.AccommodationRequest;
import jsbh.Jusangbokhap.api.accommodation.dto.AccommodationResponse;
import jsbh.Jusangbokhap.api.accommodation.dto.AccommodationResponse.Search;
import jsbh.Jusangbokhap.domain.accommodation.Accommodation;
import jsbh.Jusangbokhap.domain.accommodation.AccommodationType;
import jsbh.Jusangbokhap.domain.accommodation.QAccommodation;
import jsbh.Jusangbokhap.domain.accommodation.repository.AccommodationRepository;
import jsbh.Jusangbokhap.domain.availableDate.QAvailableDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccommodationGuestService {

    private final AccommodationRepository accommodationRepository;

    public List<AccommodationResponse> find(AccommodationRequest.Search filter) {
        Predicate predicate = buildPredicate(filter);

        List<Accommodation> accommodations = accommodationRepository.findAvailableAccommodations(predicate);
        List<AccommodationResponse> responses = new ArrayList<>();
        for (Accommodation accommodation : accommodations) {
            Search search = new Search(
                    accommodation.getAccommodationId(),
                    accommodation.getBusinessName(),
                    accommodation.getTitle(),
                    accommodation.getAddress().getFullAddress(),
                    accommodation.getAccommodationPrice().getPrice(),
                    ChronoUnit.DAYS.between(filter.checkin(), filter.checkout()) * accommodation.getAccommodationPrice().getPrice()
            );
            responses.add(search);
        }
        return responses;
    }

    public List<AccommodationCoordSearchResponse> findByCoordinate(AccommodationCoordSearchRequest coordSearchRequest) {

        Double radius = coordSearchRequest.getRadius();

        // 반경을 최대 50km 까지만 가능하게 만듦. radius 는 m 단위로 옴.
        if (radius > 50000d) {
            radius = 50000d;
        }

        // convert meters To latitude degree -> 공식: 도(°) = 미터(m) / 111000
        Double radiusToLatDegree = radius / 111_000;

        List<Accommodation> accommodations =
                accommodationRepository.findAccommodationByCoordinate(coordSearchRequest.getLongitude(),
                        coordSearchRequest.getLatitude(),
                        radiusToLatDegree,
                        coordSearchRequest.getLastAccommodationId(),
                        10);

        return accommodations.stream()
                .map(AccommodationCoordSearchResponse::of)
                .toList();
    }


    private Predicate buildPredicate(AccommodationRequest.Search filter) {
        BooleanBuilder builder = new BooleanBuilder();

        builder = filterByBusinessName(builder, filter.businessName());
        builder = filterByGuests(builder, filter.guests());
        builder = filterByDate(builder, filter.checkin(), filter.checkout());
        builder = filterByPrice(builder, filter.minPrice(), filter.maxPrice());
        builder = filterByType(builder, filter.type());
        builder = filterByAddress(builder, filter.sido(), filter.sigungu(), filter.eupmyeondong(), filter.detail());

        log.info("Generated SQL: {}", builder.toString());
        return builder;
    }

    private BooleanBuilder filterByBusinessName(BooleanBuilder builder, String businessName) {
        if (businessName != null && !businessName.trim().isEmpty()) {
            builder.and(QAccommodation.accommodation.businessName.contains(businessName));
        }
        return builder;
    }

    private BooleanBuilder filterByGuests(BooleanBuilder builder, Integer guests) {
        if (guests != null) {
            builder.and(QAccommodation.accommodation.maxGuests.maxGuest.goe(guests));
        }
        return builder;
    }

    private BooleanBuilder filterByDate(BooleanBuilder builder, LocalDate checkin, LocalDate checkout) {
        if (checkin != null && checkout != null) {
            QAvailableDate availableDate = QAvailableDate.availableDate;

            builder.and(availableDate.checkin.loe(checkin));
            builder.and(availableDate.checkout.goe(checkout));
        }
        return builder;
    }

    private BooleanBuilder filterByPrice(BooleanBuilder builder, Integer minPrice, Integer maxPrice) {
        if (minPrice != null && maxPrice != null) {
            builder.and(QAccommodation.accommodation.accommodationPrice.price.between(minPrice, maxPrice));
        }
        return builder;
    }

    private BooleanBuilder filterByType(BooleanBuilder builder, AccommodationType type) {
        if (type != null) {
            builder.and(QAccommodation.accommodation.accommodationType.eq(type));
        }
        return builder;
    }

    private BooleanBuilder filterByAddress(BooleanBuilder builder, String sido, String sigungu, String eupmyeondong, String detail) {

        if (sido != null && !sido.trim().isEmpty()) {
            builder.and(QAccommodation.accommodation.address.sido.eq(sido));
        }

        if (sigungu != null && !sigungu.trim().isEmpty()) {
            builder.and(QAccommodation.accommodation.address.sigungu.eq(sigungu));
        }

        if (eupmyeondong != null && !eupmyeondong.trim().isEmpty()) {
            builder.and(QAccommodation.accommodation.address.eupmyeondong.eq(eupmyeondong));
        }

        if (detail != null && !detail.trim().isEmpty()) {
            builder.and(QAccommodation.accommodation.address.detail.eq(detail));
        }

        return builder;
    }
}
