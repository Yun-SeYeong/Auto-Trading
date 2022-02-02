package demo.coin;

import demo.coin.dao.CoinHistory;
import demo.coin.dao.CoinHistoryKey;
import demo.coin.dao.TradeHistory;
import demo.coin.repository.CoinHistoryRepository;
import demo.coin.repository.TradeHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
public class BackTester {

    @Autowired
    CoinHistoryRepository coinHistoryRepository;

    @Autowired
    TradeHistoryRepository tradeHistoryRepository;

    @Test
    @Transactional
    @Rollback(value = false)
    void startBackTest() {
        LocalDate startDate = LocalDate.of(2021, 1, 1);
        LocalDate endDate = LocalDate.of(2022, 1, 31);
        String testCoin = "ETH";
        double startMoney = 10000;

        tradeHistoryRepository.deleteAll();

        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            CoinHistory currentCoinHistory = coinHistoryRepository.getById(CoinHistoryKey.builder()
                            .createdTime(date)
                            .name(testCoin)
                    .build());

            CoinHistory preCoinHistory = coinHistoryRepository.getById(CoinHistoryKey.builder()
                    .createdTime(date.minusDays(1))
                    .name(testCoin)
                    .build());

            System.out.println("coinHistory = " + currentCoinHistory);
            System.out.println("preCoinHistory = " + preCoinHistory);

            double range = preCoinHistory.getMaxPrice() - preCoinHistory.getMinPrice();
            double k = 0.6;
            double target = currentCoinHistory.getOpeningPrice() + range * k;

            List<TradeHistory> tradeHistoryList = tradeHistoryRepository.getAllByIsBuy("Y");

            System.out.println("tradeHistoryList = " + tradeHistoryList);

            for (TradeHistory tradeHistory : tradeHistoryList) {
                double sellMoney = (currentCoinHistory.getOpeningPrice() / tradeHistory.getBuyPrice()) * tradeHistory.getBuyMoney();

                tradeHistory.setSellTime(date);
                tradeHistory.setIsBuy("N");
                tradeHistory.setSellPrice(currentCoinHistory.getOpeningPrice());
                tradeHistory.setSellMoney(sellMoney);
                tradeHistory.setBuyPrice(tradeHistory.getBuyPrice());

                startMoney = sellMoney;

                System.out.println("[SELL] tradeHistory = " + tradeHistory);
                tradeHistoryRepository.save(tradeHistory);
            }

            if (startMoney > 0 && currentCoinHistory.getMaxPrice() > target) {

                TradeHistory tradeHistory = TradeHistory.builder()
                        .createdTime(date)
                        .name(testCoin)
                        .isBuy("Y")
                        .buyPrice(target)
                        .buyMoney(startMoney)
                        .build();

                System.out.println("[BUY] tradeHistory = " + tradeHistory);
                tradeHistoryRepository.save(tradeHistory);
                startMoney = 0.0;
            }

        }
    }
}
