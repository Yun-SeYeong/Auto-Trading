package demo.coin.controller;

public interface CoinHistoryService {
    void downloadHistory();
    void makeOrder() throws Exception;
    void collectCoin() throws Exception;
}
