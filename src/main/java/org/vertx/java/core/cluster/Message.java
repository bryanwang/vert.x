package org.vertx.java.core.cluster;

import org.jboss.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.ServerID;

/**
 * <p>Represents a message sent on the event bus.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Message extends Sendable {

  private static final Logger log = Logger.getLogger(Message.class);

  ServerID sender;
  boolean requiresReply;
  EventBus bus;

  /**
   * The unique id of the message - this is filled in by the event bus when the message is sent
   */
  public String messageID;

  /**
   * The address where the message is being sent
   */
  public String address;

  /**
   * The body (payload) of the message
   */
  public final Buffer body;

  /**
   * Create a new Message
   * @param address The address to send the message to
   * @param body
   */
  public Message(String address, Buffer body) {
    this.address = address;
    this.body = body;
  }

  /**
   * Reply to this message. If the message was sent specifying a receipt handler, that handler will be
   * called when it has received a reply. If the message wasn't sent specifying a receipt handler
   * this method does nothing.
   * Replying to a message this way is equivalent to sending a message to an address which is the same as the message id
   * of the original message.
   */
  public void reply(Buffer body) {
    if (bus != null && requiresReply) {
      if (body == null) {
        body = Buffer.create(0);
      }
      bus.send(new Message(messageID, body));
    }
  }

  /**
   * Same as {@link #reply(Buffer)} but with an empty buffer
   */
  public void reply() {
    reply(null);
  }

  Message(Buffer readBuff) {
    // TODO Meh. This could be improved
    int pos = 1;
    int messageIDLength = readBuff.getInt(pos);
    pos += 4;
    byte[] messageIDBytes = readBuff.getBytes(pos, pos + messageIDLength);
    pos += messageIDLength;
    messageID = new String(messageIDBytes, CharsetUtil.UTF_8);

    int addressLength = readBuff.getInt(pos);
    pos += 4;
    byte[] addressBytes = readBuff.getBytes(pos, pos + addressLength);
    pos += addressLength;
    address = new String(addressBytes, CharsetUtil.UTF_8);

    int port = readBuff.getInt(pos);
    pos += 4;
    int hostLength = readBuff.getInt(pos);
    pos += 4;
    byte[] hostBytes = readBuff.getBytes(pos, pos + hostLength);
    pos += hostLength;
    String host = new String(hostBytes, CharsetUtil.UTF_8);

    sender = new ServerID(port, host);

    byte bra = readBuff.getByte(pos);
    requiresReply = bra == (byte)1;
    pos += 1;

    int buffLength = readBuff.getInt(pos);
    pos += 4;
    byte[] payload = readBuff.getBytes(pos, pos + buffLength);
    body = Buffer.create(payload);
  }

  void write(NetSocket socket) {
    int length = 1 + 6 * 4 + address.length() + 1 + body.length() + messageID.length() + sender.host.length();
    Buffer totBuff = Buffer.create(length);
    totBuff.appendInt(0);
    totBuff.appendByte(Sendable.TYPE_MESSAGE);
    writeString(totBuff, messageID);
    writeString(totBuff, address);
    totBuff.appendInt(sender.port);
    writeString(totBuff, sender.host);
    totBuff.appendByte((byte)(requiresReply ? 1 : 0));
    totBuff.appendInt(body.length());
    totBuff.appendBuffer(body);
    totBuff.setInt(0, totBuff.length() - 4);
    socket.write(totBuff);
  }

  byte type() {
    return Sendable.TYPE_MESSAGE;
  }

  Message copy() {
    Message msg = new Message(address, body.copy());
    msg.messageID = this.messageID;
    msg.sender = this.sender;
    msg.requiresReply = this.requiresReply;
    return msg;
  }
}