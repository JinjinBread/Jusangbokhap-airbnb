package jsbh.Jusangbokhap.domain.accommodation.repository;

import static jsbh.Jusangbokhap.domain.accommodation.QAccommodation.accommodation;
import static jsbh.Jusangbokhap.domain.availableDate.QAvailableDate.availableDate;

import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.Query;
import jsbh.Jusangbokhap.domain.accommodation.Accommodation;
import jsbh.Jusangbokhap.domain.availableDate.AvailableDateStatus;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Repository;

@Repository
public class AccommodationRepository {

    private final EntityManager em;
    private final JPAQueryFactory queryFactory;

    public AccommodationRepository(EntityManager em) {
        this.em = em;
        this.queryFactory = new JPAQueryFactory(em);
    }

    public Accommodation save(Accommodation accommodation) {
        em.persist(accommodation);
        return accommodation;
    }

    public Optional<Accommodation> findByAccommodationId(Long accommodationId) {
        return Optional.ofNullable(em.find(Accommodation.class, accommodationId));
    }

    public List<Accommodation> findByHostId(Long hostId) {
        return em.createQuery("SELECT ac FROM Accommodation ac WHERE ac.host.id = :hostId", Accommodation.class)
                .setParameter("hostId", hostId)
                .getResultList();
    }

    public void delete(Accommodation accommodation) {
        em.remove(accommodation);
    }

    public List<Accommodation> findAvailableAccommodations(Predicate predicate) {
        return queryFactory
                .selectFrom(accommodation)
                .leftJoin(accommodation.availableDates, availableDate)
                .fetchJoin()
                .distinct()
                .where(availableDate.status.eq(AvailableDateStatus.AVAILABLE), predicate)
                .fetch();
    }

    public List<Accommodation> findAccommodationByCoordinate(Double longitude, Double latitude, Double radius,
                                                             Long lastAccommodationId, int pageSize) {

        String query = "SELECT a.* FROM accommodation a" +
                " JOIN (SELECT accommodation_address_id"+
                " FROM accommodation_address" +
                " WHERE ST_DWithin(coordinate, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :radius)) ad" +
                " ON a.accommodation_address_id = ad.accommodation_address_id";

        // 처음 조회하는 게 아니라면
        if (lastAccommodationId != null) {
            query += " AND a.accommodation_id > :lastAccommodationId";
        }

        query += " ORDER BY a.accommodation_id LIMIT :pageSize";

        Query nativeQuery = em.createNativeQuery(query, Accommodation.class)
                .setParameter("longitude", longitude)
                .setParameter("latitude", latitude)
                .setParameter("radius", radius)
                .setParameter("pageSize", pageSize);

        if (lastAccommodationId != null) {
            nativeQuery.setParameter("lastAccommodationId", lastAccommodationId);
        }

        return nativeQuery.getResultList();
    }
}
