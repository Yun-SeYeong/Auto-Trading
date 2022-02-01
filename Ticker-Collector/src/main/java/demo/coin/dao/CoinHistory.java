package demo.coin.dao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import java.time.LocalDate;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(CoinHistoryKey.class)
public class CoinHistory {
    @Id
    private LocalDate createdTime;

    @Id
    private String name;

    private double openingPrice;

    private double closingPrice;

    private double minPrice;

    private double maxPrice;

    private double unitsTraded;

    private double accTradeValue;

    private double prevClosingPrice;

    private double unitsTraded24H;

    private double  accTradeValue24H;

    private double fluctate24H;

    private double fluctateRate24H;
}
