package demo.coin.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.DayCandle;
import demo.coin.dao.MarketOrder;
import demo.coin.dao.SlackMessage;
import demo.coin.repository.DayCandleRepository;
import demo.coin.repository.MarketOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    @Scheduled(cron = "0 30 0 * * *")
    public void makeOrder() {
        LocalDateTime now = LocalDate.now().atStartOfDay();
        List<DayCandle> candleList = dayCandleRepository.findAllByLogic1(now.minusDays(1), now);

        System.out.println("candleList = " + candleList);

        int limit = 5;

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

    @Scheduled(cron = "* * 1-23 * * *")
    public void checkMarket() {
        log.debug("checkMarket");
        System.out.println("CoinScheduler.checkMarket");

        LocalDate date = LocalDate.now().minusDays(1);

        System.out.println("date = " + date);
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

            log.debug("candles = " + candles);
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
        System.out.println("coinNames = " + coinNames);

        List<String> nameList = JsonPath.read(coinNames, "$.*.market");
        System.out.println("nameList = " + nameList);

        return nameList;
    }

}
