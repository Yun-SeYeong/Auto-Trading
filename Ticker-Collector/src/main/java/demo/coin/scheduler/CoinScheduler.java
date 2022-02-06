package demo.coin.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import demo.coin.dao.DayCandle;
import demo.coin.dao.SlackMessage;
import demo.coin.repository.DayCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CoinScheduler {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DayCandleRepository dayCandleRepository;

    @Scheduled(cron = "0 0 9 * * *")
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
