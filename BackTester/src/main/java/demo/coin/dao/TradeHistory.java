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
@IdClass(TradeHistoryKey.class)
public class TradeHistory {
    @Id
    private LocalDate createdTime;

    @Id
    private String name;

    private LocalDate sellTime;

    private String isBuy;

    private double buyPrice;

    private double sellPrice;

    private double buyMoney;

    private double sellMoney;
}
