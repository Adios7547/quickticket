package com.quickticket.common.config;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis 수동 구성.
 * mybatis-spring-boot-starter가 아직 Spring Boot 4를 지원하지 않아
 * 자동설정 대신 SqlSessionFactory를 직접 등록한다. (mybatis.* 프로퍼티는 동작하지 않음)
 */
@Configuration
@MapperScan(basePackages = "com.quickticket", annotationClass = Mapper.class)
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        // resultType에 패키지 없이 클래스명만 쓸 수 있도록 도메인 패키지 등록
        factoryBean.setTypeAliasesPackage("com.quickticket.event.domain");
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:mapper/**/*.xml"));

        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true); // seat_no 컬럼 -> seatNo 필드 자동 매핑
        factoryBean.setConfiguration(configuration);

        return factoryBean.getObject();
    }
}
