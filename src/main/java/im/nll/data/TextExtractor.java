package im.nll.data;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TextExtractor {
    private static final Logger logger = LoggerFactory
            .getLogger(TextExtractor.class);
    private static Pattern p_chinese;
    private static Matcher m_chinese;
    private static Pattern p_punctuation;
    private static Matcher m_punctuation;
    // 文本密度，论坛一半在10以上，新闻要小很多，在5以上
    private static int textDensity = 5;
    // 中文字符密度（中文字数/文本长度）
    private static double textRate = 0.5;
    // 是否开启标点检查
    private static boolean checkPunctuation = true;
    // 是否开启中文检查
    private static boolean checkChinese = true;

    public static void main(String[] args) {
        // String url = "http://www.madcn.net/?p=791"; //非新闻类文章测试
        // 需要关闭标点检查，文本密度调整为5
        // String url = "http://tieba.baidu.com/p/1158988989";
        // String source = new HttpCrawler().crawl(url);
        // String content = extract(source);
        // logger.info("正文内容");
        // logger.info("------------------------------");
        // logger.info(content);
        String text = "2011年07月05日 09:03   来源：   编辑：杨苏雯";
        double d = getTextRate(text);
        System.out.println(d);
    }

    /**
     * 抽取标题
     *
     * @param source      html源码
     * @param textDensity 每行的中文文本密度
     * @return 正文
     */
    public static String extract(String source, int textDensity) {
        TextExtractor.textDensity = textDensity;
        return extract(source);
    }

    /**
     * 抽取标题
     *
     * @param source           html源码
     * @param textDensity      每行的中文文本密度
     * @param checkPunctuation 是否开启标点检查（默认开启）
     * @return 正文
     */
    public static String extract(String source, int textDensity,
                                 boolean checkPunctuation) {
        TextExtractor.textDensity = textDensity;
        TextExtractor.checkPunctuation = checkPunctuation;
        return extract(source);
    }

    /**
     * 抽取标题
     *
     * @param source           html源码
     * @param textDensity      每行的中文文本密度
     * @param checkPunctuation 是否开启标点检查（默认开启）
     * @param checkChinese     是否开启中文检查（默认开启）
     * @return 正文
     */
    public static String extract(String source, int textDensity,
                                 boolean checkPunctuation, boolean checkChinese) {
        TextExtractor.textDensity = textDensity;
        TextExtractor.checkPunctuation = checkPunctuation;
        TextExtractor.checkChinese = checkChinese;
        return extract(source);
    }

    /**
     * 抽取正文
     *
     * @param source
     * @return 正文
     */
    public static String extract(String source) {
        if (!isContentPage(source, true)) {
            return "不为内容页面";
        }
        long start = System.currentTimeMillis();
        Elements elements = processSource(source);
        // 记录每种tag的数量（暂时还没用到）
        // Map<Element, Integer> tagNum = new MapMaker().weakKeys().weakValues()
        // .makeMap();
        // 记录每种tag的最大文本数
        Map<Element, Integer> maxTagLength = new HashMap<Element, Integer>();
        // 过滤之后纳入检测的节点
        LinkedHashMap<Element, String> texts = new LinkedHashMap<Element, String>();
        processDefaultTags(elements, maxTagLength, texts);
        // logger.debug("节点数:" + tagNum);
        // logger.debug("节点长度:" + maxTagLength);
        // 记录文本长度
        int maxSize = 0;
        String maxTagName = "";
        Element maxTag = null;
        // 获取最长文本的节点名称
        for (Entry<Element, Integer> one : maxTagLength.entrySet()) {
            if (maxSize < one.getValue()) {
                maxSize = one.getValue();
                maxTagName = one.getKey().tagName();
                maxTag = one.getKey();
            }
        }
        logger.debug("最长文本标签:" + maxTagName);
        StringBuilder tempContent = new StringBuilder();
        processCenterTags(texts, maxTagName, maxSize, tempContent, maxTag);
        // logger.info("前置标签:" + beforeTags);
        // 暂时不处理前置标签
        // tempContent = processBeforeTags(maxSize, tempContent, beforeTags);
        long end = System.currentTimeMillis();
        logger.debug("抽取正文耗时:" + (end - start));
        elements = null;
        maxTagLength = null;
        texts = null;
        return tempContent.toString();
    }

    /**
     * 处理正文标签
     *
     * @param texts
     * @param maxTagName
     * @param tempContent
     * @return 前置标签
     */
    private static Map<Element, String> processCenterTags(
            Map<Element, String> texts, String maxTagName, int maxSize,
            StringBuilder tempContent, Element maxTag) {
        int exist = 0, last = 0, firstTag = 0, sameNum = 0;
        Map<Element, String> beforeTags = new HashMap<Element, String>();
        Element centerTag = null;
        LinkedHashMap<Element, String> centerTags = new LinkedHashMap<Element, String>();
        StringBuilder tempHtml = new StringBuilder();
        for (Entry<Element, String> one : texts.entrySet()) {
            Element element = one.getKey();
            String tagName = element.tagName();
            String ownText = element.ownText();
            logger.debug(element.tagName() + " : " + element.ownText());
            // 前置标签
            if (exist == 0) {
                beforeTags.put(element, tagName);
            }
            // 解决贴吧第一个标签就为最大标签的问题。可能不太严谨
            if (firstTag == 0) {
                if (tagName.equals(maxTagName) && texts.size() > 1) {
                    continue;
                }
            } else {
                if (exist < 1) {
                    if (tagName.equals(maxTagName)
                            && (getChineseNum(ownText) > textDensity
                            && getPunctuationNum(ownText) > 0 && getTextRate(ownText) > textRate)
                            && (ownText.indexOf("登录") > 20 || ownText
                            .indexOf("登录") == -1)) {
                        exist++;
                    } else {
                        continue;
                    }
                }
            }
            firstTag++;
            // 如果节点名称等于文本最长节点名称，同时中文字符大于textDensity,包含标点符号
            if (tagName.equals(maxTagName)) {
                // 如果两个标签相差小于2,把中间的节点也追加上
                StringBuilder sb = new StringBuilder();
                for (Entry<Element, String> tempElement : centerTags.entrySet()) {
                    if (centerTag.tagName().equals(tempElement.getValue())) {
                        if (tempHtml.indexOf(tempElement.getKey().toString()) != -1) {
                            last = 0;
                        } else {
                            sb.append(tempElement.getKey().toString()).append(
                                    "<p>");
                            last = 0;
                        }
                    }
                }
                sameNum = 0;
                centerTags.clear();
                tempHtml.append(sb).append(element);
                tempContent.append(sb).append(element);
                // 如果第一个取到的标签小于最大文本长度
            } else {
                if (exist != 0) {
                    centerTag = element;
                    // 中间夹着的标签
                    if (centerTags.containsValue(element.tagName())) {
                        centerTags.put(element, element.tagName());
                        sameNum++;
                        logger.debug("sameTagNum:" + sameNum);
                        if (sameNum > 5) {
                            break;
                        }
                        continue;
                    } else {
                        last++;
                    }
                    centerTags.put(element, element.tagName());
                }
                // 如果已经找到前置节点，这里处理后置节点
                if (exist > 0) {
                    // 如果后面的两个节点依然不是最大节点标签，那么退出循环
                    if (last == 2) {
                        if (tempContent.indexOf(maxTag.toString()) == -1) {
                            tempContent.append(maxTag.toString());
                            last = 0;
                        }
                        // 如果下面两个节点是包含在上面的节点中，继续追加
                        if (StringUtils.countMatches(tempHtml.toString(),
                                element.html()) > 0) {
                            tempContent.append(element);
                            last = 1;
                        } else {
                            break;
                        }
                        break;
                    }
                }
            }
        }
        centerTags = null;
        return beforeTags;
    }

    /**
     * 处理标签
     *
     * @param elements
     * @param maxTagLength
     * @param texts
     */
    private static void processDefaultTags(Elements elements,
                                           Map<Element, Integer> maxTagLength, Map<Element, String> texts) {
        for (Element element : elements) {
            String nodeValue = element.ownText();
            // 不为空的节点才进行计算
            if (isNotNullOrEmpty(nodeValue)
                    && nodeValue.indexOf("©") == -1
                    && nodeValue.indexOf("»") == -1
                    && nodeValue.indexOf("①") == -1) {
                maxTagLength.put(element, getChineseNum(nodeValue));
                texts.put(element, nodeValue);
                logger.debug(element.tagName() + " : " + element.ownText());
            }
        }
    }

    /**
     * 处理源代码，返回标签
     *
     * @param source
     * @return Elements
     */
    private static Elements processSource(String source) {
        Validate.checkNotNull(source, "html source is null or empty!");
        long start = System.currentTimeMillis();
        // 执行html格式化清理
        source = Jsoup.parse(source).html();
        // 格式化为松散形html代码
        source = Jsoup.clean(source, Whitelist.relaxed());
        // 获取body标签中包含中文的节点
        Elements elements = Jsoup.parse(body(source))
                .getElementsMatchingText("[\u4e00-\u9fa5]");
        long end = System.currentTimeMillis();
        logger.debug("格式化html耗时:" + (end - start));
        // 移除body标签标签
        if (elements.size() > 0) {
            elements.remove(0);
        }
        return elements;
    }

    /**
     * 检查中文个数
     *
     * @param str
     * @return
     */
    private static int getChineseNum(String str) {
        int count = 0;
        if (checkChinese) {
            String regEx = "[\\u4e00-\\u9fa5]";
            p_chinese = Pattern.compile(regEx);
            m_chinese = p_chinese.matcher(str);
            while (m_chinese.find())
                count++;
        } else {
            count = textDensity + 1;
        }
        return count;
    }

    /**
     * 检查标点符号
     *
     * @param str
     * @return
     */
    private static int getPunctuationNum(String str) {
        int count = 0;
        if (checkPunctuation) {
            String regEx = "[, ,，.．。“”?!！、]";
            p_punctuation = Pattern.compile(regEx);
            m_punctuation = p_punctuation.matcher(str.trim());
            while (m_punctuation.find())
                count++;
        } else {
            count = 1;
        }
        return count;
    }

    /**
     * 获取中文比率
     *
     * @param text
     * @return
     */
    private static double getTextRate(String text) {
        int chinese_num = getChineseNum(text);
        return (double) chinese_num / text.length();
    }

    /**
     * 判断是否为包含正文的网页
     *
     * @param htmlText 源码
     * @param clearTag 是否清理html标签
     * @return
     */
    private static boolean isContentPage(String htmlText, boolean clearTag) {
        String text = clearTag ? clearHtmlTag(htmlText) : htmlText;
        int periodCount = 0;
        for (int i = 0; i < text.length() && periodCount < 3; i++) {
            if (text.charAt(i) == '，' || text.charAt(i) == '。')
                periodCount++;
        }
        return periodCount >= 3;
    }

    private static String clearHtmlTag(String html) {
        return Jsoup.parse(html).text();
    }


    /**
     * 获取html源代码中的body部分.
     * <p/>
     * 如果没有body标签，那么返回全部
     *
     * @param html
     * @return
     */
    private static String body(String html) {
        int start_index = html.toLowerCase().indexOf("<body");
        int end_index = html.toLowerCase().lastIndexOf("</body>");
        if (start_index < 0) {
            start_index = 0;
        }
        if (end_index < 0) {
            end_index = html.length();
        }
        return html.substring(start_index, end_index);
    }

    private static boolean isNullOrEmpty(String content) {
        return content == null || content.length() == 0
                || "null".equals(content);
    }

    private static boolean isNotNullOrEmpty(String content) {
        return (content != null) && (content.length() > 0);
    }

}
