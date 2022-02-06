package demo.coin;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.CoinHistory;
import demo.coin.dao.CoinHistoryKey;
import demo.coin.dao.TradeHistory;
import demo.coin.dto.Balance;
import demo.coin.repository.CoinHistoryRepository;
import demo.coin.repository.TradeHistoryRepository;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
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
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@SpringBootTest
public class BackTester {

    @Autowired
    CoinHistoryRepository coinHistoryRepository;

    @Autowired
    TradeHistoryRepository tradeHistoryRepository;

    @Autowired
    ObjectMapper objectMapper;

    private final String accessKey = "dPqjPTmcluZqUGGkQxwOZtNrnlHPCiAMOk3S2z6s";
    private final String secretKey = "7C6CrYWkxnxnMSIGoig8UNgJ3EDQB47eituYU0Bj";

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
    void tradeTest() throws Exception{
        LocalDate localDate = LocalDate.now();
        String testCoin = "BTC";

        sellAllCoin();

        CoinHistory preCoinHistory = coinHistoryRepository.getById(CoinHistoryKey.builder()
                .createdTime(localDate.minusDays(1))
                .name(testCoin)
                .build());

        System.out.println("preCoinHistory = " + preCoinHistory);

    }

    @Test
    void getCoinInfo(String coin) throws Exception {
        String serverUrl = "https://api.upbit.com";

        HashMap<String, String> params = new HashMap<>();
        params.put("market", "KRW-" + coin);

        String queryString = getQueryString(params);
        String authenticationToken = getAuthenticationTokenWithParam(params);

        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpGet request = new HttpGet(serverUrl + "/v1/orders/chance?" + queryString);
            request.setHeader("Content-Type", "application/json");
            request.addHeader("Authorization", authenticationToken);

            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            String entityJson = EntityUtils.toString(entity, "UTF-8");

            System.out.println("entityJson = " + entityJson);

            String bidFee = JsonPath.read(entityJson, "$.bid_fee");
            String askFee = JsonPath.read(entityJson, "$.ask_fee");

            String bidMinTotal = JsonPath.read(entityJson, "$.market.bid.min_total");
            String askMinTotal = JsonPath.read(entityJson, "$.market.ask.min_total");

            System.out.println("bidFee = " + bidFee);
            System.out.println("askFee = " + askFee);
            System.out.println("bidMinTotal = " + bidMinTotal);
            System.out.println("askMinTotal = " + askMinTotal);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void sellAllCoin() throws Exception{
        List<Balance> balanceList = getBalance();

        for (Balance balance : balanceList) {
            if (!balance.getCurrency().equals("KRW")) {
                System.out.println("balance = " + balance);

                HashMap<String, String> params = new HashMap<>();
                params.put("market", "KRW-" + balance.getCurrency());
                params.put("side", "ask");
                params.put("volume", balance.getBalance());
                params.put("ord_type", "market");

                orderCoin(params);
            }
        }
    }

    @Test
    void orderCoin(HashMap<String, String> params) throws Exception {
        String serverUrl = "https://api.upbit.com";
        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpPost request = new HttpPost(serverUrl + "/v1/orders");
            request.setHeader("Content-Type", "application/json");
            request.addHeader("Authorization", getAuthenticationTokenWithParam(params));
            request.setEntity(new StringEntity(new Gson().toJson(params)));

            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getOrderBook() {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        Mono<String> orderBookMono = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/orderbook")
                        .queryParam("markets", "KRW-BTC")
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class);

        String orderBook = orderBookMono.block();
        System.out.println("orderBook = " + orderBook);
    }

    String getQueryString(HashMap<String, String> params) {
        ArrayList<String> queryElements = new ArrayList<>();
        for(Map.Entry<String, String> entity : params.entrySet()) {
            queryElements.add(entity.getKey() + "=" + entity.getValue());
        }

        return String.join("&", queryElements.toArray(new String[0]));
    }

    String getAuthenticationTokenWithParam(HashMap<String, String> params) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(getQueryString(params).getBytes("UTF-8"));

        String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .withClaim("query_hash", queryHash)
                .withClaim("query_hash_alg", "SHA512")
                .sign(algorithm);

        return "Bearer " + jwtToken;
    }

    String getAuthenticationToken() {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        return "Bearer " + jwtToken;
    }

    @Test
    List<Balance> getBalance() throws Exception {
        String serverUrl = "https://api.upbit.com";

        String authenticationToken = getAuthenticationToken();

        try {
            CloseableHttpClient client = HttpClientBuilder.create().build();
            HttpGet request = new HttpGet(serverUrl + "/v1/accounts");
            request.setHeader("Content-Type", "application/json");
            request.addHeader("Authorization", authenticationToken);

            HttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();

            String responseJson = EntityUtils.toString(entity, "UTF-8");

            System.out.println(response.getStatusLine());
            System.out.println(responseJson);

            List<Balance> balanceList = objectMapper.readValue(responseJson, new TypeReference<List<Balance>>() {});
            System.out.println("balanceList = " + balanceList);

            return balanceList;
        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new Exception("자산정보를 조회할 수 없습니다.");
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
