/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "28007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // Configure the server.
        //todo SingleThreadEventExecutor组成group.
        //todo NioEventLoopGroup->MultithreadEventLoopGroup
        //todo children = new SingleThreadEventExecutor[nThreads]; SingleThreadEventExecutor 也就是NioEventLoop
        //todo EventLoop 中包含Selector和处理逻辑。
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(); //todo 默认coreNum*2
        try {

            ServerBootstrap b = new ServerBootstrap();
            //todo ServerBootstrap包含group和childGroup, handler和childHandler
            //todo group() 初始化group和childGroup，返回BootStrap。
            //todo channel 初始化BootstrapChannelFactory，返回BootStrap。

            //todo 构造NioServerSocketChannel, channel包含NioMessageUnsafe和DefaultChannelPipeline
            //todo register->EventLoop: Bind-> pipeline.fireActive inbound
            //todo register0: 注册-> addLast注册，callHandlerCallbackLater-> pipeline.invokeHandlerAddedIfNeeded()-callHandlerAdded0-initChannel
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 100) //todo 设置backlog
             .handler(new LoggingHandler(LogLevel.INFO)) //todo 初始化BootStrap的handler
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception { //todo 初始化childHandler
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(new EchoServerHandler());
                 }
             });

            // Start the server.

            //todo 构造ChannelFuture对象
            //todo 构造channel对象， NioServerSocketChannel构造时创建ServerSocketChannel
            //todo ServerBootstrap对象中的eventGroup中，挑一个EventLoop注册channel
            //todo register0
            //todo sync() -> await
            //todo DefaultChannelHandlerContext
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
