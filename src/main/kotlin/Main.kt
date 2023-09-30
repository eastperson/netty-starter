import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.ReferenceCountUtil

fun main(args: Array<String>) {
    val discardServer = DiscardServer()
    discardServer.run()
}

class DiscardServer(
    private var port: Int = 8080,
) {

    fun run() {
        /**
         * NioEventLoopGroup은 입출력 연산을 처리하는 멀티스레드 이벤트 루프입니다.
         * netty는 다양한 종류의 transport을 위한 다양한 이벤트 루프 그룹 구현을 제공합니다.
         * 이 예제에서는 서버 측 애플리케이션을 구현하고 있으므로 두 개의 NioEventLoopGroup이 사용됩니다.
         *
         * 첫 번째는 흔히 'boss'라고 불리는 것으로, 들어오는 연결을 수락합니다.
         * 두 번째는 흔히 'worker'라고 불리는 것으로, boss가 연결을 수락하고 수락된 연결을 worker에게 등록하면 수락된 연결의 트래픽을 처리합니다.
         * 사용되는 스레드 수와 생성된 채널에 매핑되는 방식은 EventLoopGroup 구현에 따라 다르며 생성자를 통해 구성할 수도 있습니다.
         */
        val bossGroup: EventLoopGroup = NioEventLoopGroup() // (1)
        val workerGroup: EventLoopGroup = NioEventLoopGroup()

        try {
            /**
             * ServerBootstrap은 서버를 설정하는 helper 클래스입니다.
             * Channel을 사용하여 직접 서버를 설정할 수 있습니다. 그러나 대부분의 경우 그렇게 할 필요가 없습니다.
             * 여기서는 들어오는 연결을 수락하기 위해 새 채널을 인스턴스화하는 데 사용되는 NioServerSocketChannel 클래스를 사용하도록 지정합니다.
             */
            val b = ServerBootstrap() // (2)
            b.group(bossGroup, workerGroup)
                /**
                 * 여기에서는 들어오는 연결을 수락하기 위해 새 채널을 인스턴스화하는 데 사용되는
                 * NioServerSocketChannel 클래스를 사용하도록 지정합니다.
                 */
                .channel(NioServerSocketChannel::class.java) // (3)
                /**
                 * 여기에 지정된 핸들러는 항상 새로 수락된 채널에 의해 평가됩니다.
                 * ChannelInitializer는 사용자가 새 채널을 구성하는 데 도움을 주기 위한 특수 핸들러입니다.
                 * 네트워크 애플리케이션을 구현하기 위해 DiscardServerHandler와 같은 일부 핸들러를 추가하여 새 채널의 채널파이프라인을 구성하려는 경우가 대부분입니다.
                 * 애플리케이션이 복잡해지면 파이프라인에 더 많은 핸들러를 추가하고 결국 이 익명 클래스를 최상위 클래스로 추출할 가능성이 높습니다.
                 */
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(DiscardServerHandler())
                    }
                })
                /**
                 * 채널 구현에 특정한 파라미터를 설정할 수도 있습니다.
                 * 저희는 TCP/IP 서버를 작성하고 있으므로 tcpNoDelay 및 keepAlive와 같은 소켓 옵션을 설정할 수 있습니다.
                 * 지원되는 채널옵션에 대한 개요를 보려면 ChannelOption과 특정 ChannelConfig 구현의 apidoc 을 참조하세요.
                 */
                .option(ChannelOption.SO_BACKLOG, 128) // (5)
                /**
                 * option()은 들어오는 연결(incomming connection)을 수락하는 NioServerSocketChannel을 위한 것이고,
                 * childOption()은 부모 서버채널이 수락하는 채널(이 경우 NioSocketChannel)을 위한 것입니다.
                 */
                .childOption(ChannelOption.SO_KEEPALIVE, true) // (6)

            /**
             * 이제 준비가 완료되었습니다. 남은 것은 포트에 바인딩하고 서버를 시작하는 것입니다.
             * 여기서는 머신에 있는 모든 NIC(네트워크 인터페이스 카드)의 8080 포트에 바인딩합니다.
             * 이제 다른 바인딩 주소로 원하는 만큼 bind() 메서드를 호출할 수 있습니다.
             */
            // Bind and start to accept incoming connections.
            val f: ChannelFuture = b.bind(port).sync() // (7)

            println("start netty server")
            println("port:$port")

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync()
        } finally {
            workerGroup.shutdownGracefully()
            bossGroup.shutdownGracefully()
        }
    }
}

/**
 * DiscardServerHandler는 ChannelInboundHandler의 구현인 ChannelInboundHandlerAdapter를 확장합니다.
 * ChannelInboundHandler는 재정의할 수 있는 다양한 이벤트 핸들러 메서드를 제공합니다.
 * 현재로서는 핸들러 인터페이스를 직접 구현하기보다는 ChannelInboundHandlerAdapter를 확장하는 것으로 충분합니다.
 */
class DiscardServerHandler : ChannelInboundHandlerAdapter() {

    /**
     * . 이 메서드는 클라이언트로부터 새 데이터가 수신될 때마다 수신된 메시지와 함께 호출됩니다.
     * 이 예제에서 수신된 메시지의 유형은 ByteBuf입니다.
     */
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val `in` = msg as ByteBuf
        try {
            while (`in`.isReadable) { // (1)
                print(Char(`in`.readByte().toUShort()))
                System.out.flush()
            }
        } finally {
            ReferenceCountUtil.release(msg) // (2)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        // Close the connection when an exception is raised.
        cause.printStackTrace()
        ctx.close()
    }
}
