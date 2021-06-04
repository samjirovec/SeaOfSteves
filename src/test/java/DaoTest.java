import io.github.samjirovec.SeaOfSteves.Model.PlayerMeta;
import io.github.samjirovec.SeaOfSteves.Model.SeaOfStevesPlayer;
import io.github.samjirovec.SeaOfSteves.Utils.Dao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class DaoTest {

  static Dao dao;

  @BeforeAll
  static void setup() {
    dao = new Dao();
  }

  @Test
  void save() {
    Map<String, SeaOfStevesPlayer> foo = new HashMap<>();
    SeaOfStevesPlayer player = SeaOfStevesPlayer.builder().playerMeta(new PlayerMeta(100)).build();
    foo.put("ASDF", player);
    dao.saveMap(foo);
  }

  @Test
  void retrieve() {
    dao.retrieveAllPlayers();
  }
}
