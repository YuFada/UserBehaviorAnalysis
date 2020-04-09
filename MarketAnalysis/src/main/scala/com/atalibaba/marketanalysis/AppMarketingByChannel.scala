package com.atalibaba.marketanalysis

import java.sql.Timestamp
import java.util.UUID
import java.util.concurrent.TimeUnit

import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.{RichSourceFunction, SourceFunction}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import scala.util.Random

/**
  * @author :YuFada
  * @date ： 2020/4/1 0001 下午 22:06
  *       Description：
  *       分渠道的市场推广统计
  */
//自定义模拟数据源
case class MarketingUserBehavior(
                                    userId: String,
                                    behavior: String,
                                    channel: String,
                                    timestamp: Long
                                )

//输出结果样例类
case class MarketingViewCount(
                                 windowStart: String,
                                 windowEnd: String,
                                 channel: String,
                                 behavior: String,
                                 Count: Long
                             )

object AppMarketingByChannel {
    def main(args: Array[String]): Unit = {
        val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
        env.setParallelism(1)
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

        val dataStream = env.addSource(new SimulatedEventSource())
            .assignAscendingTimestamps(_.timestamp)
            .filter(_.behavior!="UNINSTALL")
            .map(data=>{
                ((data.channel,data.behavior),1L) //后面给个1   最后的count值
            })
            .keyBy(_._1) //以渠道和行为作为key进行分组
            .timeWindow(Time.hours(1),Time.seconds(10))
            .process(new MarketingCountByChannel())

        dataStream.print()
        env.execute("app marketing By channel job")

    }

}

//自定义数据源  模拟数据
class SimulatedEventSource() extends RichSourceFunction[MarketingUserBehavior] {
    //先定义是否运行的标识位
    var running = true
    //定义用户行为的集合
    val behaviorTypes: Seq[String] = Seq("CLICK", "DOWNLOAD", "INSTALL", "UNINSTALL")
    //定义渠道的集合
    val channelSets: Seq[String] = Seq("wechat", "weibo", "appstore", "huaweistore")
    //定义一个随机发生器
    val rand: Random = new Random()

    override def cancel(): Unit = running = false

    override def run(sourceContext: SourceFunction.SourceContext[MarketingUserBehavior]): Unit = {
        //定义一个生成的数据的上限
        val maxElements = Long.MaxValue
        var count = 0L

        //随机生成所有数据
        while (running && count < maxElements) {
            val userId = UUID.randomUUID().toString
            val behavior = behaviorTypes(rand.nextInt(behaviorTypes.size))
            val channel = channelSets(rand.nextInt(channelSets.size))
            val timestamp = System.currentTimeMillis()

            sourceContext.collect(MarketingUserBehavior(userId, behavior, channel, timestamp))

            count += 1

            TimeUnit.MILLISECONDS.sleep(10L)
        }
    }

}

//自定义处理函数 I O K W    I 上面包了一个二元组 ((data.channel,data.behavior),1L)  （（Sring，String），Long）
class MarketingCountByChannel() extends ProcessWindowFunction[((String,String),Long),MarketingViewCount,(String,String),TimeWindow]{
    override def process(key: (String, String), context: Context, elements: Iterable[((String, String), Long)], out: Collector[MarketingViewCount]): Unit = {
        val startTs=new Timestamp(context.window.getStart).toString
        var endTs=new Timestamp(context.window.getEnd).toString
        val channel=key._1
        val behavior=key._2
        val count=elements.size
        out.collect(MarketingViewCount(startTs,endTs,channel,behavior,count))

    }
}