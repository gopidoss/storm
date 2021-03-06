/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.storm.rocketmq.trident;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.LocalCluster.LocalTopology;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.rocketmq.RocketMQConfig;
import org.apache.storm.rocketmq.common.mapper.FieldNameBasedTupleToMessageMapper;
import org.apache.storm.rocketmq.common.mapper.TupleToMessageMapper;
import org.apache.storm.rocketmq.common.selector.DefaultTopicSelector;
import org.apache.storm.rocketmq.common.selector.TopicSelector;
import org.apache.storm.rocketmq.trident.state.RocketMQState;
import org.apache.storm.rocketmq.trident.state.RocketMQStateFactory;
import org.apache.storm.rocketmq.trident.state.RocketMQStateUpdater;
import org.apache.storm.trident.Stream;
import org.apache.storm.trident.TridentTopology;
import org.apache.storm.trident.state.StateFactory;
import org.apache.storm.trident.testing.FixedBatchSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

import java.util.Properties;

public class WordCountTrident {

    public static StormTopology buildTopology(String nameserverAddr, String topic){
        Fields fields = new Fields("word", "count");
        FixedBatchSpout spout = new FixedBatchSpout(fields, 4,
                new Values("storm", 1),
                new Values("trident", 1),
                new Values("needs", 1),
                new Values("javadoc", 1)
        );
        spout.setCycle(true);

        TupleToMessageMapper mapper = new FieldNameBasedTupleToMessageMapper("word", "count");
        TopicSelector selector = new DefaultTopicSelector(topic);

        Properties properties = new Properties();
        properties.setProperty(RocketMQConfig.NAME_SERVER_ADDR, nameserverAddr);

        RocketMQState.Options options = new RocketMQState.Options()
                .withMapper(mapper)
                .withSelector(selector)
                .withProperties(properties);

        StateFactory factory = new RocketMQStateFactory(options);

        TridentTopology topology = new TridentTopology();
        Stream stream = topology.newStream("spout1", spout);

        stream.partitionPersist(factory, fields,
                new RocketMQStateUpdater(), new Fields());

        return topology.build();
    }

    public static void main(String[] args) throws Exception {
        Config conf = new Config();
        conf.setMaxSpoutPending(5);
        if (args.length == 2) {
            try (LocalCluster cluster = new LocalCluster();
                 LocalTopology topo = cluster.submitTopology("wordCounter", conf, buildTopology(args[0], args[1]));) {
                Thread.sleep(60 * 1000);
            }
            System.exit(0);
        }
        else if(args.length == 3) {
            conf.setNumWorkers(3);
            StormSubmitter.submitTopology(args[2], conf, buildTopology(args[0], args[1]));
        } else{
            System.out.println("Usage: WordCountTrident <nameserver addr> <topic> [topology name]");
        }
    }

}
