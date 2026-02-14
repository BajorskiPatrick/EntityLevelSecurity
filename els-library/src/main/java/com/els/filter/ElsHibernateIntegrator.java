package com.els.filter;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.hibernate.type.BasicType;
import org.hibernate.type.StandardBasicTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Hibernate Integrator that automatically applies ELS filter mappings
 * to all entity classes at integration time.
 *
 * Filter definitions (elsFilter, elsBlacklistFilter) must be declared via
 * package-info.java or other mechanism. This integrator adds the actual
 * Filter mappings to each entity's PersistentClass.
 *
 * Registered via META-INF/services/org.hibernate.integrator.spi.Integrator
 */
public class ElsHibernateIntegrator implements Integrator {

    private static final Logger log = LoggerFactory.getLogger(ElsHibernateIntegrator.class);

    private static final String WHITELIST_FILTER = "elsFilter";
    private static final String BLACKLIST_FILTER = "elsBlacklistFilter";

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext,
            SessionFactoryImplementor sessionFactory) {

        int entityCount = 0;
        for (PersistentClass pc : metadata.getEntityBindings()) {
            if (pc.getIdentifier() != null) {
                pc.addFilter(WHITELIST_FILTER, "id IN (:ids)", true, Map.of(), Map.of());
                pc.addFilter(BLACKLIST_FILTER, "id NOT IN (:ids)", true, Map.of(), Map.of());
                entityCount++;
                log.debug("ELS | Applied filters to entity: {}", pc.getEntityName());
            }
        }

        log.info("ELS | Hibernate filters applied to {} entities.", entityCount);
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory,
            SessionFactoryServiceRegistry serviceRegistry) {
        // No cleanup needed
    }
}
