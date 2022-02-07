package demo.coin.repository;

import demo.coin.dao.MarketOrder;
import demo.coin.dao.MarketOrderKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MarketOrderRepository extends JpaRepository<MarketOrder, MarketOrderKey> {

    List<MarketOrder> findAllByCandleDateTimeUtc(LocalDateTime dateTime);
}
