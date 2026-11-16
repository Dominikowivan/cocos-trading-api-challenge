package com.cocos.trading.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.cocos.trading.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
abstract class PersistenceTestBase {

}
