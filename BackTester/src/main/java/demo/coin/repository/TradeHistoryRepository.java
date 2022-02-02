package demo.coin.repository;

import demo.coin.dao.TradeHistory;
import demo.coin.dao.TradeHistoryKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, TradeHistoryKey> {

    List<TradeHistory> getAllByIsBuy(String isBuy);
}
