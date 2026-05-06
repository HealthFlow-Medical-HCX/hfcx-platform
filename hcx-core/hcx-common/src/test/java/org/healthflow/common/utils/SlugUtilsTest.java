package org.healthflow.common.utils;

import org.junit.Assert;
import org.junit.Test;

public class SlugUtilsTest {

    @Test
    public void testMakeSlug() {
        String sluggified =  SlugUtils.makeSlug("settlements@hospital.com" , "", ".", "healthflow-hcx-egypt");
        Assert.assertEquals("settlements.hospital@healthflow-hcx-egypt", sluggified);
    }

}
