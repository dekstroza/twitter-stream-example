package org.dekstroza.examples.storm;

import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Created by deki on 08/01/15.
 * oo-yeah, baby
 */
public class TwitterSpout extends BaseRichSpout {

    private static final Logger LOG = LoggerFactory.getLogger(TwitterSpout.class);
    private final TwitterUtil twitterUtil;
    private String track;
    private SpoutOutputCollector collector;

    /**
     * Default constructor
     *
     * @param twitterKeys name of the property file containing twitter auth keys
     */
    public TwitterSpout(final String twitterKeys) {
        this.twitterUtil = new TwitterUtil(twitterKeys);
    }

    @Override
    public void declareOutputFields(final OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("criteria", "tweet"));
    }

    @Override
    public void open(final Map map, final TopologyContext topologyContext, final SpoutOutputCollector spoutOutputCollector) {
        final int spoutsSize = topologyContext.getComponentTasks(topologyContext.getThisComponentId()).size();
        final int myIdx = topologyContext.getThisTaskIndex();
        final String[] tracks = ((String) map.get("track")).split(",");
        final StringBuffer tracksBuffer = new StringBuffer();
        for (int i = 0; i < tracks.length; i++) {
            if (i % spoutsSize == myIdx) {
                tracksBuffer.append(",");
                tracksBuffer.append(tracks[i]);
            }
        }

        if (tracksBuffer.length() == 0)
            throw new RuntimeException("No track found for spout" +
                    " [spoutsSize:" + spoutsSize + ", tracks:" + tracks.length + "] the amount" +
                    " of tracks must be more then the spout paralellism");

        track = tracksBuffer.toString();
        this.collector = spoutOutputCollector;
    }

    @Override
    public void nextTuple() {

        try {
            final InputStream inputStream = this.twitterUtil.openTwitterStreamForTrack(track);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String in;
            //Read line by line
            while ((in = reader.readLine()) != null) {
                try {
                    //Parse and emit
                    final Object json = new JSONParser().parse(in);
                    collector.emit(new Values(track, json));
                } catch (ParseException e) {
                    LOG.error("Error parsing message from twitter", e);
                }
            }
        } catch (final IOException e) {
            LOG.error("Error in communication with twitter api.");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
        }

    }
}
