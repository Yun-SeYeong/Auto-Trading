package demo.coin.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.CoinHistory;
import demo.coin.dao.DayCandle;
import demo.coin.dao.MarketOrder;
import demo.coin.dao.SlackMessage;
import demo.coin.dto.Balance;
import demo.coin.repository.CoinHistoryRepository;
import demo.coin.repository.DayCandleRepository;
import demo.coin.repository.MarketOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoinHistoryServiceImpl implements CoinHistoryService{
    private final CoinHistoryRepository coinHistoryRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final DayCandleRepository dayCandleRepository;
    private final ObjectMapper objectMapper;


    private final String accessKey = "dPqjPTmcluZqUGGkQxwOZtNrnlHPCiAMOk3S2z6s";
    private final String secretKey = "7C6CrYWkxnxnMSIGoig8UNgJ3EDQB47eituYU0Bj";

    String getTicker() throws ExecutionException, InterruptedException {
        WebClient client = WebClient.create("https://api.bithumb.com/public/ticker");

        Mono<String> coinsMono = client.get()
                .uri("/ALL_KRW")
                .retrieve()
                .bodyToMono(String.class);

        String coins = coinsMono.toFuture().get();

        System.out.println("coins = " + coins);
        return coins;
    }

    Set<String> getCoinNames() throws ExecutionException, InterruptedException {
        String json = getTicker();

        Set<String> names = JsonPath.read(json, "$.data.keys()");

        for (String name : names) {
            System.out.println("name = " + name);
        }
        return names;
    }

    @Override
    public void downloadHistory() {
        new Thread(() -> {
            Set<String> coinNames = null;
            try {
                coinNames = getCoinNames();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
            WebClient client = WebClient.create("https://api.bithumb.com/public/candlestick");

            int total = coinNames.size() - 1;
            int i = 1;

            for (String coinName: coinNames) {
                if (!coinName.equals("date")) {
                    Mono<String> coinsMono = client.get()
                            .uri("/" + coinName + "_KRW_24H")
                            .retrieve()
                            .bodyToMono(String.class);

                    String coins = null;
                    try {
                        coins = coinsMono.toFuture().get();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    System.out.println("coins = " + coins);

                    List<List<?>> coinList = JsonPath.read(coins, "$.data");

                    System.out.println("coinList.get(0).get(0) = " + Double.parseDouble(coinList.get(0).get(0).toString()));
                    System.out.println("time"
                            + Instant.ofEpochMilli((Long) coinList.get(0).get(0)).atZone(ZoneId.systemDefault()).toLocalDate());

                    for (List<?> coinDayHistory : coinList) {
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
                    log.info("진행도: " + i + "/" + total + " [" + (((double) i/total)*100) + "%]");
                    i++;
                }
            }
        }).start();

    }

    public void makeOrder() throws Exception {
        LocalDateTime now = LocalDate.now().atStartOfDay();
        List<DayCandle> candleList = dayCandleRepository.findAllByLogic1(now.minusDays(1), now);

        //System.out.println("candleList = " + candleList);

        marketOrderRepository.deleteAll();

        int limit = 10;

        WebClient client = WebClient.create("https://api.upbit.com/v1");

        for (int i = 0; i < limit; i++) {

            int finalI = i;
            Mono<String> candlesMono = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/candles/days")
                            .queryParam("market", candleList.get(finalI).getMarket())
                            .queryParam("count", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class);

            String candles = candlesMono.toFuture().get();

            List<DayCandle> dayCandleList = objectMapper.readValue(candles, new TypeReference<List<DayCandle>>() {});

            BigDecimal k = new BigDecimal(1).subtract(candleList.get(i).getTradePrice().subtract(candleList.get(i).getOpeningPrice())
                    .divide(candleList.get(i).getHighPrice().subtract(candleList.get(i).getLowPrice()), RoundingMode.HALF_UP));

            MarketOrder order = MarketOrder.builder()
                    .market(candleList.get(i).getMarket())
                    .candleDateTimeUtc(candleList.get(i).getCandleDateTimeUtc())
                    .targetPrice((candleList.get(i).getHighPrice()
                            .subtract(candleList.get(i).getLowPrice()))
                            .multiply(k)
                            .add(dayCandleList.get(0).getOpeningPrice()))
                    .build();

            //System.out.println("order = " + order);
            sendSlackHook(SlackMessage.builder()
                    .text("[주문 생성] " + order + " \n[K] " + k)
                    .build());

            marketOrderRepository.save(order);

            Thread.sleep(1000);
        }
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

    void sendSlackHook(SlackMessage slackMessage) {
        new Thread(() -> {
            WebClient webHookClient = WebClient.create("https://hooks.slack.com/services");
            webHookClient.post()
                    .uri("/T031X6E1M1A/B031N7EHEQ6/zOuoEqp6sSjzauMFyIVmRC4I")
                    .bodyValue(slackMessage)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        }).start();

    }

    public void collectCoin() throws Exception {
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

            String candles = candlesMono.toFuture().get();

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

    List<String> getMarketName() throws ExecutionException, InterruptedException {
        WebClient client = WebClient.create("https://api.upbit.com/v1");

        Mono<String> coinNamesMono = client.get()
                .uri("/market/all")
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class);

        String coinNames = coinNamesMono.toFuture().get();
        //System.out.println("coinNames = " + coinNames);

        List<String> nameList = JsonPath.read(coinNames, "$.*.market");
        //System.out.println("nameList = " + nameList);

        return nameList;
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
}
