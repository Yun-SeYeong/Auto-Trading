package demo.coin;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import demo.coin.dao.CoinHistory;
import demo.coin.dao.CoinHistoryKey;
import demo.coin.dao.TradeHistory;
import demo.coin.repository.CoinHistoryRepository;
import demo.coin.repository.TradeHistoryRepository;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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


    @Test
    @Transactional
    @Rollback(value = false)
    void tradeTest() {
        LocalDate localDate = LocalDate.now();
        String testCoin = "BTC";

        WebClient client = WebClient.create("https://api.bithumb.com");


        Mono<String> balanceMono = client.post()
                .uri("/info/balance")
                .retrieve()
                .bodyToMono(String.class);

        String BalanceStr = balanceMono.block();



        CoinHistory preCoinHistory = coinHistoryRepository.getById(CoinHistoryKey.builder()
                .createdTime(localDate.minusDays(1))
                .name(testCoin)
                .build());

        System.out.println("preCoinHistory = " + preCoinHistory);


    }

    @Test
    void getBalance() {
        String accessKey = "발급받은 Access key";
        String secretKey = "발급받은 Secret key";
        String serverUrl = "https://api.upbit.com";

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        String authenticationToken = "Bearer " + jwtToken;

        try {
            CloseableHttpClient client = HttpClientBuilder.create().build();
            HttpGet request = new HttpGet(serverUrl + "/v1/accounts");
            request.setHeader("Content-Type", "application/json");
            request.addHeader("Authorization", authenticationToken);

            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            System.out.println(response.getStatusLine());
            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getJWTToken() {
        String accessKey = "발급받은 Access key";
        String secretKey = "발급받은 Secret key";

        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        String authenticationToken = "Bearer " + jwtToken;
        System.out.println("authenticationToken = " + authenticationToken);
    }
}
