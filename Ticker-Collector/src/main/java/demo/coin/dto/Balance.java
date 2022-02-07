package demo.coin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Balance {
    String currency;
    String balance;
    String locked;
    String avg_buy_price;
    Boolean avg_buy_price_modified;
    String unit_currency;
}
