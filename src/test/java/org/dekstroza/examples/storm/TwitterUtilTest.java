package org.dekstroza.examples.storm;

import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;

/**
 * Created by deki on 08/01/15.
 */

public class TwitterUtilTest {

    @Test
    public void openTwitterStreamForTrack()
    {

        TwitterUtil tut = new TwitterUtil("/Users/deki/Desktop/twitter_keys.properties");
        InputStream is = tut.openTwitterStreamForTrack("France");
        Assert.assertNotNull(is);
    }
}
