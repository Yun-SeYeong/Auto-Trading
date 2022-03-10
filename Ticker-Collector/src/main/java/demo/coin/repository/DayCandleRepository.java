package demo.coin.repository;

import demo.coin.dao.DayCandle;
import demo.coin.dao.DayCandleKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DayCandleRepository extends JpaRepository<DayCandle, DayCandleKey> {

    @Query(value = "select dc from DayCandle dc where dc.candleDateTimeUtc >= :startDate and dc.candleDateTimeUtc < :endDate and dc.market like 'KRW-%' order by (dc.tradePrice - dc.openingPrice) / (dc.highPrice - dc.lowPrice) * (dc.openingPrice * 100 / dc.tradePrice) desc")
    List<DayCandle> findAllByLogic1(@Param(value = "startDate") LocalDateTime startDate, @Param(value = "endDate") LocalDateTime endDate);
}
