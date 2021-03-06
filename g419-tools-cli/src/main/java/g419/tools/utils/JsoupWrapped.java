package g419.tools.utils;

import org.apache.log4j.Logger;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;

import java.net.SocketTimeoutException;

public class JsoupWrapped {

  /**
   * Pobiera treść strony do pięciu timieout-ów.
   *
   * @param url Adres strony do pobrania
   * @return
   */
  public static Document get(String url) {
    Document doc = null;
    int timeout = 0;
    try {
      while (timeout >= 0 && timeout < 5) {
        try {
          Logger.getLogger(JsoupWrapped.class).info("Jsoup: " + url);
          doc = Jsoup.connect(url)
              .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
              .referrer("http://www.google.com")
              .timeout(10000)
              .get();
          timeout = -1;
        } catch (SocketTimeoutException ex) {
          Logger.getLogger(JsoupWrapped.class).warn(
              String.format("Timeout: %d z 5", ++timeout));
        }
      }
    } catch (HttpStatusException ex) {
      if (ex.getStatusCode() == 404) {
        Logger.getLogger(JsoupWrapped.class).info("Ostatnia strona (Status=404)");
      } else {
        Logger.getLogger(JsoupWrapped.class).info("HttpStatusException (" + ex.getStatusCode() + ")");
        ex.printStackTrace();
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    if (doc == null) {
      Logger.getLogger(JsoupWrapped.class).info("Strona nie została pobrana");
    }

    return doc;
  }

  public static Document getWithSelenium(WebDriver driver, String url) {
    boolean repeat = true;
    String html = null;
    driver.get(url);
    while (repeat) {
      try {
        html = driver.getPageSource();
        try {
          Thread.sleep(10000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        if (html.contains("To continue, please type the characters below:")) {

        } else {
          repeat = false;
        }
      } catch (Exception ex) {

      }
    }
    return Jsoup.parse(html);
  }

  public static void br2nl(Document document) {
    document.outputSettings(new Document.OutputSettings().prettyPrint(false));//makes html() preserve linebreaks and spacing
    document.select("br").append("\\n");
    document.select("p").prepend("\\n\\n");
    //String s = document.html().replaceAll("\\\\n", "\n");
    //return Jsoup.clean(s, "", Whitelist.none(), new Document.OutputSettings().prettyPrint(false));
  }
}
