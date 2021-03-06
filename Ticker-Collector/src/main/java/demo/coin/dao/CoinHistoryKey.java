package demo.coin.dao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinHistoryKey implements Serializable {
    private LocalDate createdTime;
    private String name;
}
