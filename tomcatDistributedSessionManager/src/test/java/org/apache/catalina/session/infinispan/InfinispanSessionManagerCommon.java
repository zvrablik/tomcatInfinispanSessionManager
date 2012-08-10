/* --------------------------------------------------------------*\
| Copyright (C) e-Spatial Solutions Limited, All rights reserved. |
\* --------------------------------------------------------------*/
package org.apache.catalina.session.infinispan;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.infinispan.manager.DefaultCacheManager;

/**
 * InfinispanSessionManagerCommon
 * User: zvrablikhenek
 * Since: 6/29/12
 */
public class InfinispanSessionManagerCommon {

    /**
     * Get initialized session manager
     * @return
     * @throws org.apache.catalina.LifecycleException
     */
    public static InfinispanSessionManager getInitializedManager(String name, boolean distributed) throws LifecycleException {
        InfinispanSessionManager infinispanSessionManager = new InfinispanSessionManager();

        StandardEngine engine = new StandardEngine();
        engine.setDomain("domain" + name);
        engine.setName("name" + name);
        engine.setDefaultHost("defaultHost" + name);
        engine.setBaseDir("baseDir" + name);


        StandardContext context = new StandardContext();
        context.setParent(engine);
        context.setName("contextName" + name);
        context.setDistributable(distributed);

        infinispanSessionManager.setContainer(context);

        infinispanSessionManager.init();
        infinispanSessionManager.start();

        return infinispanSessionManager;
    }
}
