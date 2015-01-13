package org.dekstroza.examples.storm;

import backtype.storm.task.TopologyContext;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Tuple;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by deki on 08/01/15.
 * oo-yeah, baby
 */
public class TwitterSumarizeHashTags extends BaseBasicBolt {

    private static final Logger LOG = LoggerFactory.getLogger(TwitterSumarizeHashTags.class);
    Map<String, Integer> hashtags = new HashMap<String, Integer>();

    private final String redisIp;
    private final String redisPort;


    public TwitterSumarizeHashTags(final String ip, final String port) {
        super();
        this.redisIp = ip;
        this.redisPort = port;
    }

    @Override
    public void cleanup() {

    }

    @Override
    public void execute(Tuple input, BasicOutputCollector collector) {
        JSONObject json = (JSONObject) input.getValueByField("tweet");
        if (json.containsKey("entities")) {
            JSONObject entities = (JSONObject) json.get("entities");
            if (entities.containsKey("hashtags")) {
                for (Object hashObj : (JSONArray) entities.get("hashtags")) {
                    JSONObject hashJson = (JSONObject) hashObj;
                    String hash = hashJson.get("text").toString().toLowerCase();
                    //                    LOG.info("Hash is {}",hash);
                    if (!hashtags.containsKey(hash)) {
                        hashtags.put(hash, 1);
                    } else {
                        Integer last = hashtags.get(hash);
                        hashtags.put(hash, last + 1);
                    }
                }
            }
        }
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context) {
        TimerTask timerTask = new TimerTask() {
            private Jedis jedis = new Jedis(redisIp, Integer.parseInt(redisPort));
            @Override
            public void run() {
                Map<String, Integer> oldMap = new HashMap<String, Integer>(hashtags);
                final JSONObject jsonObj = new JSONObject();
                final JSONArray jsonArray = new JSONArray();

                hashtags.clear();
                for (Map.Entry<String, Integer> entry : oldMap.entrySet()) {
                    LOG.info("{}:{}", new Object[] { entry.getKey(), entry.getValue() });
                    JSONObject singleHash = new JSONObject();
                    singleHash.put("hashtag", entry.getKey());
                    singleHash.put("count", entry.getValue());
                    jsonArray.add(singleHash);
                }
                jsonObj.put("trending", jsonArray);
                jedis.publish("hashtags", jsonObj.toJSONString());
            }

        };
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 10000, 10000);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }

}
