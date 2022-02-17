package demo.coin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/v1")
@RequiredArgsConstructor
public class CoinHistoryController {

    private final CoinHistoryService coinHistoryService;

    @PostMapping("/coin_history")
    ResponseEntity<String> postCoinHistory() {
        coinHistoryService.downloadHistory();
        return new ResponseEntity("Success", HttpStatus.OK);
    }

    @PostMapping("/collect_coin")
    ResponseEntity<String> collectCoin() throws Exception {
        coinHistoryService.collectCoin();
        return new ResponseEntity("Success", HttpStatus.OK);
    }

    @PostMapping("/make_order")
    ResponseEntity<String> makeOrder() throws Exception {
        coinHistoryService.makeOrder();
        return new ResponseEntity("Success", HttpStatus.OK);
    }
}
