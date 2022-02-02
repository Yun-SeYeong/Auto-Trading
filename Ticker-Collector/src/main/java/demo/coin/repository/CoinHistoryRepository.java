package demo.coin.repository;

import demo.coin.dao.CoinHistory;
import demo.coin.dao.CoinHistoryKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinHistoryRepository extends JpaRepository<CoinHistory, CoinHistoryKey> {
}
