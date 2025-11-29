package com.els.jdbc;

import com.els.logger.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import javax.sql.DataSource;

public class AclDataSourceBeanPostProcessor implements BeanPostProcessor {

    private final Logger logger = Logger.getInstance();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource && !(bean instanceof AclDataSourceProxy)) {
            logger.log("Wrapping DataSource in Proxy: " + beanName);
            return new AclDataSourceProxy((DataSource) bean);
        }
        return bean;
    }
}