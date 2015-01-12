package org.dekstroza.examples.storm;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.topology.TopologyBuilder;

/**
 * Created by deki on 08/01/15.
 * oo-yeah, baby
 */
public class ExampleTwitterTopology {

    /**
     * Main class
     *
     * @param args First argument list of comma separated tweet keywords, second argument is property file name containing twitter auth keys
     */
    public static void main(final String[] args) {
        final TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("tweets-collector", new TwitterSpout(args[1]));

        builder.setBolt("hashtag-sumarizer", new TwitterSumarizeHashTags(args[2], args[3])).
                shuffleGrouping("tweets-collector");

        final LocalCluster cluster = new LocalCluster();
        final Config config = new Config();
        config.put("track", args[0]);
        cluster.submitTopology("twitter-test", config, builder.createTopology());
    }
}
