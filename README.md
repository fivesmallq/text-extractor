# text-extractor
简单的网页正文提取


```java
        String url = "http://news.cnblogs.com/n/541621/";
        String html = Request.Get(url).execute().returnContent().asString();
        String text = TextExtractor.from(html).extract();
        System.out.println(text);
```
