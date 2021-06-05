package io.github.samjirovec.SeaOfSteves.Model;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PlayerMeta {
  @Expose private Emissary emissary;
  @Expose private int money;
}
