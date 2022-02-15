package demo.coin.scheduler;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.DayCandle;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class CoinScheduler {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DayCandleRepository dayCandleRepository;

    @Autowired
    MarketOrderRepository marketOrderRepository;

    private int todayStartMoney = -1;

    private final String accessKey = "dPqjPTmcluZqUGGkQxwOZtNrnlHPCiAMOk3S2z6s";
    private final String secretKey = "7C6CrYWkxnxnMSIGoig8UNgJ3EDQB47eituYU0Bj";

    @Scheduled(cron = "0 30 0 * * *")
    public void makeOrder() {
        LocalDateTime now = LocalDate.now().atStartOfDay();
        List<DayCandle> candleList = dayCandleRepository.findAllByLogic1(now.minusDays(1), now);

        //System.out.println("candleList = " + candleList);

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

            //System.out.println("order = " + order);
            sendSlackHook(SlackMessage.builder()
                    .text("[주문 생성] " + order)
                    .build());

            marketOrderRepository.save(order);
        }

        sendSlackHook(SlackMessage.builder()
                .text("Start Money: " + todayStartMoney)
                .build());
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void sellCoins() throws Exception {
        sellAllCoin();
        sendSlackHook(SlackMessage.builder()
                .text("Sell All Coins")
                .build());

        todayStartMoney = getKRWByBalances(getWallet());
    }

    @Scheduled(cron = "* * 1-23 * * *")
    public void checkMarket() throws Exception {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        LocalDate lastDay = LocalDate.now().minusDays(1);

        List<MarketOrder> marketOrderList = marketOrderRepository.findAllByCandleDateTimeUtc(lastDay.atStartOfDay());

        if (marketOrderList.size() > 0) {
            List<Balance> balanceList = getWallet();

            int money = getKRWByBalances(balanceList);

            if (todayStartMoney < 0) {
                todayStartMoney = money;
            }

            money = money > 5000 ? money - 5000 : 0;


            ///////////////////////////////////////////////////////////////////////////

            Map<String, MarketOrder> targetMap = new HashMap<>();

            Mono<String> orderbookMono = client.get()
                    .uri(uriBuilder -> {
                        UriBuilder builder = uriBuilder
                                .path("/orderbook");
                        for (MarketOrder marketOrder : marketOrderList) {
                            System.out.println("marketOrder = " + marketOrder);
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

            for (Orderbook ob : orderbookList) {
                Orderbook.OrderbookUnit unit = ob.getOrderbookUnits().get(0);

                String coinName = ob.getMarket().replace("KRW-", "");
                boolean isCoinBuy = checkCoin(balanceList, coinName);
                List<MinuteCandle> minuteCandleList = getMinuteCandle(ob.getMarket());
                BigDecimal ma10 = getMa(minuteCandleList, 10);

                if (money > 0
                        && unit.getAskPrice().compareTo(targetMap.get(ob.getMarket()).getTargetPrice()) > 0
                        && unit.getAskPrice().compareTo(targetMap.get(ob.getMarket()).getTargetPrice().multiply(BigDecimal.valueOf(1.01))) < 0
                        && unit.getAskPrice().compareTo(ma10) > 0
                        && !isCoinBuy) {

                    sendSlackHook(SlackMessage.builder()
                            .text("[매수] Coin: " + ob.getMarket() + " Target: " + targetMap.get(ob.getMarket()))
                            .build());

                    buyCoin(ob.getMarket(), String.valueOf(money));
                    targetMap.get(ob.getMarket()).setBuyTime(LocalDateTime.now());
                    marketOrderRepository.save(targetMap.get(ob.getMarket()));
                }

                if (isCoinBuy && unit.getBidPrice().compareTo(targetMap.get(ob.getMarket()).getTargetPrice().multiply(BigDecimal.valueOf(1.05))) > 0) {
                    sendSlackHook(SlackMessage.builder()
                            .text("[매도] Coin: " + ob.getMarket() + " Price: " + unit.getBidPrice() + "( 5% 이상 상승 )")
                            .build());

                    sellCoin(ob.getMarket());

                    int currentMoney =  getKRWByBalances(getWallet());
                    sendLossMessage(((double) (currentMoney) / todayStartMoney) * 100);
                    todayStartMoney = currentMoney;
                }

                if (isCoinBuy && unit.getBidPrice().compareTo(targetMap.get(ob.getMarket()).getTargetPrice().multiply(BigDecimal.valueOf(0.98))) < 0) {
                    sendSlackHook(SlackMessage.builder()
                            .text("[매도] Coin: " + ob.getMarket() + " Price: " + unit.getBidPrice() + "( 2% 이상 하락 )")
                            .build());

                    sellCoin(ob.getMarket());

                    int currentMoney =  getKRWByBalances(getWallet());
                    sendLossMessage(((double) (currentMoney) / todayStartMoney) * 100);
                    todayStartMoney = currentMoney;
                }

                if (isCoinBuy && unit.getBidPrice().compareTo(ma10) < 0 && unit.getBidPrice().compareTo(targetMap.get(ob.getMarket()).getTargetPrice().multiply(BigDecimal.valueOf(0.99))) < 0) {
                    sendSlackHook(SlackMessage.builder()
                            .text("[매도] Coin: " + ob.getMarket() + " Price: " + unit.getBidPrice() + "( 이동평균선 이탈로 인한 손절 [ma10] )")
                            .build());

                    sellCoin(ob.getMarket());

                    int currentMoney =  getKRWByBalances(getWallet());
                    sendLossMessage(((double) (currentMoney) / todayStartMoney) * 100);
                    todayStartMoney = currentMoney;
                }

                if (isCoinBuy && unit.getBidPrice().compareTo(ma10) < 0 && unit.getBidPrice().compareTo(targetMap.get(ob.getMarket()).getTargetPrice().multiply(BigDecimal.valueOf(1.01))) > 0) {
                    sendSlackHook(SlackMessage.builder()
                            .text("[매도] Coin: " + ob.getMarket() + " Price: " + unit.getBidPrice() + "( 이동평균선 이탈로 인한 익절 [ma10] )")
                            .build());

                    sellCoin(ob.getMarket());

                    int currentMoney =  getKRWByBalances(getWallet());
                    sendLossMessage(((double) (currentMoney) / todayStartMoney) * 100);
                    todayStartMoney = currentMoney;
                }
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void run() throws Exception{
        log.info("Collecting start...");

        sendSlackHook(SlackMessage.builder()
                .text("Start collecting...")
                .build());

        List<String> marketNames = getMarketName();

        WebClient client = WebClient.create("https://api.upbit.com/v1");

        int i = 0;
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

//            log.debug("candles = " + candles);
            sendSlackHook(SlackMessage.builder()
                    .text("[COLLECT] COIN:" + marketName + " (" + i + "/" + marketNames.size() + ")[" + ((int) (((double) i/marketNames.size())*100)) + "%]")
                    .build());

            List<DayCandle> dayCandleList = objectMapper.readValue(candles, new TypeReference<List<DayCandle>>() {});

            dayCandleList.remove(dayCandleList.get(0));

            dayCandleRepository.saveAll(dayCandleList);

            Thread.sleep(1000);
            i++;
        }

        log.info("Collecting success!");

        sendSlackHook(SlackMessage.builder()
                .text("End collecting.")
                .build());
    }

    void sendSlackHook(SlackMessage slackMessage) {
        WebClient webHookClient = WebClient.create("https://hooks.slack.com/services");

        webHookClient.post()
                .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                .bodyValue(slackMessage)
                .retrieve()
                .bodyToMono(String.class)
                .block();
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

    List<MinuteCandle> getMinuteCandle(String marketName) throws JsonProcessingException {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        Mono<String> candleMinuteMono = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/candles/minutes/3")
                        .queryParam("market", marketName)
                        .queryParam("count", 20)
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

        ma = ma.divide(BigDecimal.valueOf(maNum));

        //System.out.println("ma" + maNum + " = " + ma);
        return ma;
    }

    void sendLossMessage(double loss) {
        sendSlackHook(SlackMessage.builder()
                .text(loss > 0 ? "[익절] " : "[손절] " + loss + "%")
                .build());
    }
}
