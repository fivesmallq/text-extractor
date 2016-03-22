package im.nll.data;

import org.apache.http.client.fluent.Request;
import org.junit.Test;

/**
 * @author <a href="mailto:fivesmallq@gmail.com">fivesmallq</a>
 * @version Revision: 1.0
 * @date 16/3/22 下午3:03
 */
public class TextExtractorTest {

    @Test
    public void testExtract() throws Exception {
        String url = "http://news.cnblogs.com/n/541621/";
        String html = Request.Get(url).execute().returnContent().asString();
        String text = TextExtractor.from(html).extract();
        System.out.println(text);
    }
}
