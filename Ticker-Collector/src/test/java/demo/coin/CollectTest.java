package demo.coin;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.*;
import demo.coin.dto.Balance;
import demo.coin.dto.MinuteCandle;
import demo.coin.dto.Orderbook;
import demo.coin.repository.CoinHistoryRepository;
import demo.coin.repository.DayCandleRepository;
import demo.coin.repository.MarketOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@SpringBootTest
@RequiredArgsConstructor
@Slf4j
public class CollectTest {

    @Autowired
    CoinHistoryRepository coinHistoryRepository;

    @Autowired
    DayCandleRepository dayCandleRepository;

    @Autowired
    MarketOrderRepository marketOrderRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${upbit.access-key}")
    private final String accessKey;

    @Value("${upbit.secret-key}")
    private final String secretKey;

    @Test
    String getTickerTest() {
        WebClient client = WebClient.create("https://api.bithumb.com/public/ticker");

        Mono<String> coinsMono = client.get()
                .uri("/ALL_KRW")
                .retrieve()
                .bodyToMono(String.class);

        String coins = coinsMono.block();

        System.out.println("coins = " + coins);
        return coins;
    }

    @Test
    Set<String> getCoinNames() {
        String json = getTickerTest();

        Set<String> names = JsonPath.read(json, "$.data.keys()");

        for (String name : names) {
            System.out.println("name = " + name);
        }
        return names;
    }

    @Test
    void checkMarket() throws Exception {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        LocalDate lastDay = LocalDate.now().minusDays(1);
        System.out.println("date = " + lastDay);

        int money = getKRW();
        money = money > 10000 ? money - 10000 : 0;

        List<MarketOrder> marketOrderList = marketOrderRepository.findAllByCandleDateTimeUtc(lastDay.atStartOfDay());

        Map<String, BigDecimal> targetMap = new HashMap<>();

        Mono<String> orderbookMono = client.get()
                .uri(uriBuilder -> {
                    UriBuilder builder = uriBuilder
                            .path("/orderbook");
                    for (MarketOrder marketOrder : marketOrderList) {
                        System.out.println("marketOrder = " + marketOrder);
                        builder.queryParam("markets", marketOrder.getMarket());
                        targetMap.put(marketOrder.getMarket(), marketOrder.getTargetPrice());
                    }
                    return builder.build();
                })
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class);

        String orderBook = orderbookMono.block();

        List<Orderbook> orderbookList = objectMapper.readValue(orderBook, new TypeReference<>() {});

        for (Orderbook ob : orderbookList) {
            System.out.println("orderbook = " + ob);

            Orderbook.OrderbookUnit unit = ob.getOrderbookUnits().get(ob.getOrderbookUnits().size()-1);

            System.out.println("unit = " + ob.getMarket());
            System.out.println("unit.getBidSize() = " + unit.getBidPrice());
            System.out.println("targetMap.get(ob.getMarket()) = " + targetMap.get(ob.getMarket()));

            if (unit.getBidPrice().compareTo(targetMap.get(ob.getMarket())) > 0 && money > 0) {
                System.out.println("[??????] ob.getMarket() = " + ob.getMarket());


            }
        }
    }

    @Test
    void sellAllCoin() throws Exception{
        List<Balance> balanceList = getWallet();

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
    void buyBTC() throws Exception {
        buyCoin("KRW-BTC", "5000");
    }

    @Test
    void buyCoin(String market, String money) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("market", market);
        params.put("side", "bid");
        params.put("price", money);
        params.put("ord_type", "price");

        orderCoin(params);
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
    void checkWallet() throws Exception {
        getWallet();
    }

    @Test
    void checkKRW() throws Exception {
        System.out.println("getKRW() = " + getKRW());
    }
    
    @Test
    void checkCoinExist() throws Exception {
        System.out.println("checkCoin(\"BTC\") = " + checkCoin("BTC"));
    }

    @Test
    void getMinuteCandleTest() throws JsonProcessingException {
        List<MinuteCandle> minuteCandleList = getMinuteCandle();

        BigDecimal ma10 = getMa(minuteCandleList, 10);

        System.out.println("ma10 = " + ma10);
    }

    List<MinuteCandle> getMinuteCandle() throws JsonProcessingException {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        Mono<String> candleMinuteMono = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/candles/minutes/3")
                        .queryParam("market", "KRW-ETC")
                        .queryParam("count", 20)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class);

        String candleMinute = candleMinuteMono.block();
        System.out.println("candleMinute = " + candleMinute);

        List<MinuteCandle> minuteCandleList = objectMapper.readValue(candleMinute, new TypeReference<>() {});
        System.out.println("minuteCandleList = " + minuteCandleList);
        return minuteCandleList;
    }

    BigDecimal getMa(List<MinuteCandle> minuteCandleList, int maNum) {
        BigDecimal ma = BigDecimal.valueOf(0);

        int i = 0;
        for (MinuteCandle candle: minuteCandleList) {
            System.out.println("candle.getCandleDateTimeKst() = " + candle.getCandleDateTimeKst());
            System.out.println("candle.getTradePrice() = " + candle.getTradePrice());

            if (i < maNum) {
                ma = ma.add(candle.getTradePrice());
            }

            i++;
        }

        ma = ma.divide(BigDecimal.valueOf(maNum));

        System.out.println("ma" + maNum + " = " + ma);
        return ma;
    }


    boolean checkCoin(String coin) throws Exception {
        boolean isExist = false;
        for (Balance balance : getWallet()) {
            if (balance.getCurrency().equals(coin)) {
                isExist = true;
                break;
            }
        }
        return isExist;
    }
    
    int getKRW() throws Exception {
        List<Balance> balanceList = getWallet();
        
        int money = 0;
        
        for (Balance balance : balanceList) {
            if (balance.getCurrency().equals("KRW")) {
                money = (int) Double.parseDouble(balance.getBalance());
            }
        }
        return money;
    }
    
    List<Balance> getWallet() throws Exception {
        System.out.println("CollectTest.getBalance");
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

        throw new Exception("??????????????? ????????? ??? ????????????.");
    }

    @Test
    List<String> getMarketName() {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        Mono<String> coinNamesMono = client.get()
                .uri("/market/all")
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class);

        String coinNames = coinNamesMono.block();
        System.out.println("coinNames = " + coinNames);

        List<String> nameList = JsonPath.read(coinNames, "$.*.market");
        System.out.println("nameList = " + nameList);

        return nameList;
    }

    @Test
    void sellCoinByMarketName() throws Exception {
        sellCoin("KRW-WEMIX");
    }

    void sellCoin(String market) throws Exception {
        List<Balance> balanceList = getWallet();

        for (Balance balance : balanceList) {
            if (("KRW-" + balance.getCurrency()).equals(market)) {
                HashMap<String, String> params = new HashMap<>();
                params.put("market", market);
                params.put("side", "ask");
                params.put("volume", balance.getBalance());
                params.put("ord_type", "market");

                orderCoin(params);
            }
        }
    }

    @Test
    void saveCoins() {
        String json = getTickerTest();

        Map<String, Map<String, ?>> coinMap = JsonPath.read(json, "$.data");

        System.out.println("coinMap = " + coinMap);

        System.out.println("coinMap.get(\"BTC\"): " + coinMap.get("BTC"));
        System.out.println("coinMap.get(\"BTC\"): " + coinMap.get("BTC").get("opening_price"));

        for (String name: coinMap.keySet()) {
            if (!name.equals("date")) {
                System.out.println("name = " + name);

                System.out.println("coinMap: " + coinMap.get(name));

                CoinHistory coinHistory = CoinHistory.builder()
                        .createdTime(LocalDate.now())
                        .name(name)
                        .openingPrice(Double.parseDouble(coinMap.get(name).get("opening_price").toString()))
                        .minPrice(Double.parseDouble(coinMap.get(name).get("min_price").toString()))
                        .maxPrice(Double.parseDouble(coinMap.get(name).get("max_price").toString()))
                        .unitsTraded(Double.parseDouble(coinMap.get(name).get("units_traded").toString()))
                        .accTradeValue(Double.parseDouble(coinMap.get(name).get("acc_trade_value").toString()))
                        .prevClosingPrice(Double.parseDouble(coinMap.get(name).get("prev_closing_price").toString()))
                        .unitsTraded24H(Double.parseDouble(coinMap.get(name).get("units_traded_24H").toString()))
                        .accTradeValue24H(Double.parseDouble(coinMap.get(name).get("acc_trade_value_24H").toString()))
                        .fluctate24H(Double.parseDouble(coinMap.get(name).get("fluctate_24H").toString()))
                        .fluctateRate24H(Double.parseDouble(coinMap.get(name).get("fluctate_rate_24H").toString()))
                        .build();

                System.out.println("coinHistory = " + coinHistory);

                coinHistoryRepository.save(coinHistory);
            }
        }
    }

    @Test
    void saveCoinHistory() throws Exception{
        WebClient webHookClient = WebClient.create("https://hooks.slack.com/services");

        webHookClient.post()
                .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                .bodyValue(SlackMessage.builder()
                        .text("Start collecting...")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        List<String> marketNames = getMarketName();

        WebClient client = WebClient.create("https://api.upbit.com/v1");

        int i = 1;
        for (String marketName : marketNames) {
            Mono<String> candlesMono = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/candles/days")
                            .queryParam("market", marketName)
                            .queryParam("count", 2)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class);

            String candles = candlesMono.block();

            System.out.println("candles = " + candles);
            webHookClient.post()
                    .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                    .bodyValue(SlackMessage.builder()
                            .text("[COLLECT] COIN:" + marketName + " (" + i + "/" + marketNames.size() + ")[" + ((int) (((double) i/marketNames.size())*100)) + "%]")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<DayCandle> dayCandleList = objectMapper.readValue(candles, new TypeReference<>() {});

            dayCandleList.remove(dayCandleList.get(0));

            dayCandleRepository.saveAll(dayCandleList);

            Thread.sleep(1000);
            i++;
        }

        webHookClient.post()
                .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                .bodyValue(SlackMessage.builder()
                        .text("End collecting.")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Test
    void makeOrder() {
        LocalDateTime now = LocalDate.now().atStartOfDay();
        List<DayCandle> candleList = dayCandleRepository.findAllByLogic1(now.minusDays(1), now);

        System.out.println("candleList = " + candleList);

        int limit = 10;

        for (int i = 0; i < limit; i++) {
            MarketOrder order = MarketOrder.builder()
                    .market(candleList.get(i).getMarket())
                    .candleDateTimeUtc(candleList.get(i).getCandleDateTimeUtc())
                    .targetPrice((candleList.get(i).getHighPrice()
                            .subtract(candleList.get(i).getLowPrice()))
                            .multiply(new BigDecimal("0.6"))
                            .add(candleList.get(i).getTradePrice()))
                    .build();

            System.out.println("order = " + order);

            marketOrderRepository.save(order);
        }
    }

    @Test
    void getCoinHistory() {
        Set<String> coinNames = getCoinNames();
        WebClient client = WebClient.create("https://api.bithumb.com/public/candlestick");

        int total = coinNames.size() - 1;
        int i = 1;
        for (String coinName: coinNames) {
            if (!coinName.equals("date")){
                Mono<String> coinsMono = client.get()
                        .uri("/" + coinName + "_KRW_24H")
                        .retrieve()
                        .bodyToMono(String.class);

                String coins = coinsMono.block();
//                System.out.println("coins = " + coins);

                List<List<?>> coinList = JsonPath.read(coins, "$.data");

                List<CoinHistoryKey> coinHistoryKeyList = new ArrayList<>();

                for (List<?> coinDayHistory : coinList) {
                    Optional<CoinHistory> coinHistoryOptional = coinHistoryRepository.findById(CoinHistoryKey.builder()
                            .name(coinName)
                            .createdTime(Instant.ofEpochMilli((Long) coinDayHistory.get(0)).atZone(ZoneId.systemDefault()).toLocalDate())
                            .build());

                    if (coinHistoryOptional.isEmpty()){
                        CoinHistory coinHistory = CoinHistory.builder()
                                .name(coinName)
                                .createdTime(Instant.ofEpochMilli((Long) coinDayHistory.get(0)).atZone(ZoneId.systemDefault()).toLocalDate())
                                .openingPrice(Double.parseDouble(coinDayHistory.get(1).toString()))
                                .closingPrice(Double.parseDouble(coinDayHistory.get(2).toString()))
                                .maxPrice(Double.parseDouble(coinDayHistory.get(3).toString()))
                                .minPrice(Double.parseDouble(coinDayHistory.get(4).toString()))
                                .unitsTraded24H(Double.parseDouble(coinDayHistory.get(5).toString()))
                                .build();

                        System.out.println("coinHistory = " + coinHistory);

                        coinHistoryRepository.save(coinHistory);
                    }
                }
            }
            log.info("?????????: " + i + "/" + total + " [" + (((double) i/total)*100) + "%]");
            i++;
        }
    }
}
