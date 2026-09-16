import java.sql.*;
public class DatabaseProbe {
  public static void main(String[] args) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:mysql://[::1]:3306/rule_engine?useSSL=false", System.getenv("MYSQL_USERNAME"), System.getenv("MYSQL_PASSWORD"))) {
      System.out.println("env_connection_ok");
      try (PreparedStatement s = c.prepareStatement("select password from rule_db_datasource where datasource_code = 'codex_source_flex_db_0916'")) {
        try (ResultSet r = s.executeQuery()) { if (r.next()) System.out.println("saved_credential_matches=" + System.getenv("MYSQL_PASSWORD").equals(r.getString(1))); }
      }
    }
  }
}
