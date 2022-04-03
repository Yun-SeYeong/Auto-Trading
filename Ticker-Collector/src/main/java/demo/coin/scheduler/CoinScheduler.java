package demo.coin.scheduler;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import demo.coin.controller.CoinHistoryService;
import demo.coin.dao.MarketOrder;
import demo.coin.dao.SlackMessage;
import demo.coin.dto.Balance;
import demo.coin.dto.MinuteCandle;
import demo.coin.dto.Orderbook;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CoinScheduler {
    private final ObjectMapper objectMapper;

    private final DayCandleRepository dayCandleRepository;

    private final MarketOrderRepository marketOrderRepository;

    private final CoinHistoryService coinHistoryService;

    private int todayStartMoney = -1;

    @Value("${upbit.access-key}")
    private String accessKey;

    @Value("${upbit.secret-key}")
    private String secretKey;

//    @Scheduled(cron = "0 30 0 * * *")
//    public void makeOrder() throws Exception {
//        coinHistoryService.makeOrder();
//    }

    @Scheduled(cron = "0 0 0 * * *")
    public void sellCoins() throws Exception {
        sellAllCoin();
        checkCurrentBalance();
    }

    @Scheduled(cron = "* * * * * *")
    public void checkMarket() throws Exception {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        LocalDate lastDay = LocalDate.now().minusDays(1);

        List<MarketOrder> marketOrderList = marketOrderRepository.findAllByCandleDateTimeUtc(lastDay.atStartOfDay());

        if (marketOrderList.size() >= 10) {
            log.info("[STEP1] get order complete");

            List<Balance> balanceList = getWallet();

            int money = getKRWByBalances(balanceList);

            if (todayStartMoney < 0) {
                todayStartMoney = money;
            }

            money = money > 5000 ? money - 5000 : 0;

            log.info("[STEP2] get balance complete");

            ///////////////////////////////////////////////////////////////////////////

            Map<String, MarketOrder> targetMap = new HashMap<>();

            Mono<String> orderbookMono = client.get()
                    .uri(uriBuilder -> {
                        UriBuilder builder = uriBuilder
                                .path("/orderbook");
                        for (MarketOrder marketOrder : marketOrderList) {
                            builder.queryParam("markets", marketOrder.getMarket());
                            targetMap.put(marketOrder.getMarket(), marketOrder);
                        }
                        return builder.build();
                    })
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class);

            String orderBook = orderbookMono.block();

            List<Orderbook> orderbookList = objectMapper.readValue(orderBook, new TypeReference<List<Orderbook>>() {});

            ///////////////////////////////////////////////////////////////////////////

            log.info("[STEP3] get order book success");

            for (Orderbook ob : orderbookList) {
                Orderbook.OrderbookUnit unit = ob.getOrderbookUnits().get(0);

                String coinName = ob.getMarket().replace("KRW-", "");
                boolean isCoinBuy = checkCoin(balanceList, coinName);

                List<MinuteCandle> minuteCandleList = getMinuteCandle(ob.getMarket(), 120,30);
                List<BigDecimal> maList = getMas(minuteCandleList);
                BigDecimal ma5 = maList.get(0);
                BigDecimal ma10 = maList.get(1);
                BigDecimal ma20 = maList.get(3);
                BigDecimal ma60 = maList.get(11);
                BigDecimal ma120 = maList.get(23);

                minuteCandleList.remove(0);
                List<BigDecimal> lastMaList = getMas(minuteCandleList);
                BigDecimal lastMa5 = lastMaList.get(0);
                BigDecimal lastMa10 = lastMaList.get(1);
                BigDecimal lastMa20 = lastMaList.get(3);
                BigDecimal lastMa60 = lastMaList.get(11);

                if (money >= 10000
                        && (lastMa10.compareTo(lastMa5) > 0 || lastMa20.compareTo(lastMa10) > 0 || lastMa60.compareTo(lastMa20) > 0)
                        && ma5.compareTo(ma10) > 0
                        && ma10.compareTo(ma20) > 0
                        && ma20.compareTo(ma60) > 0
                        && ma60.compareTo(ma120) > 0
                        && !isCoinBuy) {

                    sendSlackHook(SlackMessage.builder()
                            .text("[매수] Coin: " + ob.getMarket() + " Target: " + targetMap.get(ob.getMarket()))
                            .build());

                    buyCoin(ob.getMarket(), "10000");
                    targetMap.get(ob.getMarket()).setBuyTime(LocalDateTime.now());
                    marketOrderRepository.save(targetMap.get(ob.getMarket()));
                    break;
                }

                if (isCoinBuy
                        && unit.getBidPrice().compareTo(getCoinByBalances(balanceList, coinName).multiply(BigDecimal.valueOf(0.995))) < 0
                        && (ma10.compareTo(ma5) > 0 || ma20.compareTo(ma5) > 0 || ma60.compareTo(ma5) > 0 || ma120.compareTo(ma5) > 0)) {
                    sendSlackHook(SlackMessage.builder()
                            .text("[매도] Coin: " + ob.getMarket() + " Price: " + unit.getBidPrice() + "( 이동평균선 이탈로 인한 손절 [ma15] )")
                            .build());

                    sellCoin(ob.getMarket());

                    checkCurrentBalance();
                    break;
                }

                if (isCoinBuy
                        && unit.getBidPrice().compareTo(getCoinByBalances(balanceList, coinName).multiply(BigDecimal.valueOf(1.005))) > 0
                        && (ma10.compareTo(ma5) > 0 || ma20.compareTo(ma5) > 0 || ma60.compareTo(ma5) > 0 || ma120.compareTo(ma5) > 0)) {
                    sendSlackHook(SlackMessage.builder()
                            .text("[매도] Coin: " + ob.getMarket() + " Price: " + unit.getBidPrice() + "( 이동평균선 이탈로 인한 익절 [ma15] )")
                            .build());

                    sellCoin(ob.getMarket());

                    checkCurrentBalance();
                    break;
                }

                log.info("[STEP4] check price complete (" + ob.getMarket() + ")");
            }
        } else {
            log.info("[RECOVER] start");
            coinHistoryService.collectCoin();
            coinHistoryService.makeOrder();
            log.info("[RECOVER] end");
        }
        log.info("=====================================================");
    }

//    @Scheduled(cron = "0 0 0 * * *")
//    public void run() throws Exception{
//        coinHistoryService.collectCoin();
//    }

    void sendSlackHook(SlackMessage slackMessage) {
        WebClient webHookClient = WebClient.create("https://hooks.slack.com/services");

        webHookClient.post()
                .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                .bodyValue(slackMessage)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    void checkCurrentBalance() throws Exception {
        Thread.sleep(1000);
        List<Balance> w = getWallet();

        sendSlackHook(SlackMessage.builder()
                .text("[자산현황] " + w)
                .build());

        int currentMoney =  getKRWByBalances(w);
        sendLossMessage(((double) (currentMoney) / todayStartMoney) * 100);
        todayStartMoney = currentMoney;
    }

    List<String> getMarketName() {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        Mono<String> coinNamesMono = client.get()
                .uri("/market/all")
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class);

        String coinNames = coinNamesMono.block();
        //System.out.println("coinNames = " + coinNames);

        List<String> nameList = JsonPath.read(coinNames, "$.*.market");
        //System.out.println("nameList = " + nameList);

        return nameList;
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

    int getKRWByBalances(List<Balance> balanceList) throws Exception {
        int money = 0;

        for (Balance balance : balanceList) {
            if (balance.getCurrency().equals("KRW")) {
                money = (int) Double.parseDouble(balance.getBalance());
            }
        }
        return money;
    }

    BigDecimal getCoinByBalances(List<Balance> balanceList, String market) throws Exception {
        BigDecimal money = null;

        for (Balance balance : balanceList) {
            if (balance.getCurrency().equals(market)) {
                money = new BigDecimal(balance.getAvg_buy_price());
            }
        }

        return money;
    }

    String getAuthenticationToken() {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        return "Bearer " + jwtToken;
    }

    List<Balance> getWallet() throws Exception {
        //System.out.println("CollectTest.getBalance");
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

            //System.out.println(response.getStatusLine());
            //System.out.println(responseJson);

            List<Balance> balanceList = objectMapper.readValue(responseJson, new TypeReference<List<Balance>>() {});
            //System.out.println("balanceList = " + balanceList);

            return balanceList;
        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new Exception("자산정보를 조회할 수 없습니다.");
    }


    void sellAllCoin() throws Exception{
        List<Balance> balanceList = getWallet();

        for (Balance balance : balanceList) {
            if (!balance.getCurrency().equals("KRW")) {
                //System.out.println("balance = " + balance);

                HashMap<String, String> params = new HashMap<>();
                params.put("market", "KRW-" + balance.getCurrency());
                params.put("side", "ask");
                params.put("volume", balance.getBalance());
                params.put("ord_type", "market");

                orderCoin(params);
            }
        }
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


    void buyCoin(String market, String money) throws Exception {
        HashMap<String, String> params = new HashMap<>();
        params.put("market", market);
        params.put("side", "bid");
        params.put("price", money);
        params.put("ord_type", "price");

        orderCoin(params);
    }

    boolean checkCoin(List<Balance> balanceList, String coin) throws Exception {
        boolean isExist = false;
        for (Balance balance : balanceList) {
            if (balance.getCurrency().equals(coin)) {
                isExist = true;
                break;
            }
        }
        return isExist;
    }

    boolean checkCoinByBalances(List<Balance> balances, String coin) throws Exception {
        boolean isExist = false;
        for (Balance balance : balances) {
            if (balance.getCurrency().equals(coin)) {
                isExist = true;
                break;
            }
        }
        return isExist;
    }

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

            //System.out.println(EntityUtils.toString(entity, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    String getQueryString(HashMap<String, String> params) {
        ArrayList<String> queryElements = new ArrayList<>();
        for(Map.Entry<String, String> entity : params.entrySet()) {
            queryElements.add(entity.getKey() + "=" + entity.getValue());
        }

        return String.join("&", queryElements.toArray(new String[0]));
    }

    List<MinuteCandle> getMinuteCandle(String marketName, int count, int unit) throws JsonProcessingException {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        Mono<String> candleMinuteMono = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/candles/minutes/" + unit)
                        .queryParam("market", marketName)
                        .queryParam("count", count)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class);

        String candleMinute = candleMinuteMono.block();
        //System.out.println("candleMinute = " + candleMinute);

        List<MinuteCandle> minuteCandleList = objectMapper.readValue(candleMinute, new TypeReference<List<MinuteCandle>>() {});
        //System.out.println("minuteCandleList = " + minuteCandleList);
        return minuteCandleList;
    }

    BigDecimal getMa(List<MinuteCandle> minuteCandleList, int maNum) {
        BigDecimal ma = BigDecimal.valueOf(0);

        int i = 0;
        for (MinuteCandle candle: minuteCandleList) {
            //System.out.println("candle.getCandleDateTimeKst() = " + candle.getCandleDateTimeKst());
            //System.out.println("candle.getTradePrice() = " + candle.getTradePrice());

            if (i < maNum) {
                ma = ma.add(candle.getTradePrice());
            }

            i++;
        }

//        System.out.println("ma = " + ma);

        ma = ma.divide(BigDecimal.valueOf(maNum), RoundingMode.HALF_UP);

        //System.out.println("ma" + maNum + " = " + ma);
        return ma;
    }

    List<BigDecimal> getMas(List<MinuteCandle> minuteCandleList) {
        List<BigDecimal> decimalList = new ArrayList<>();

        BigDecimal ma = BigDecimal.valueOf(0);

        int i = 1;
        for (MinuteCandle candle: minuteCandleList) {
            ma = ma.add(candle.getTradePrice());

            if (i % 5 == 0) {
                decimalList.add(ma.divide(BigDecimal.valueOf(i), RoundingMode.HALF_UP));
            }

            i++;
        }

        return decimalList;
    }

    void sendLossMessage(double loss) {
        sendSlackHook(SlackMessage.builder()
                .text((loss > 100 ? "[익절] " + (loss - 100)  : "[손절] " + (100 - loss)) + "%")
                .build());
    }
}
