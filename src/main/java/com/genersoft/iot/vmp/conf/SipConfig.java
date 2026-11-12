package com.genersoft.iot.vmp.conf;


import com.genersoft.iot.vmp.vmanager.bean.SipIdentity;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "sip", ignoreInvalidFields = true)
@Order(0)
@Data
public class SipConfig {

	private String ip;

	private String showIp;

	private List<String> monitorIps;

	private Integer port;

	private String domain;

	private String id;

	private String password;

	private boolean alarm = true;

	private long timeout = 1000;

	/**
	 * SIP(TCP) 是否使用协议栈的 NIO 消息处理器。
	 * 默认 false：使用协议栈默认的阻塞 IO(TCPMessageProcessor)，与历史稳定版本一致。
	 * 注意：不要使用带同步连通性预检的自定义处理器，它会在协议栈分发线程上阻塞建链、失败时直接丢弃消息。
	 */
	private boolean nioMessageProcessor = false;

	/**
	 * 身份三元组快照。只在身份热更新时整体替换, 读取时一次性取回,
	 * 避免并发读取观察到 id/domain/password 的交叉组合。
	 */
	private volatile SipIdentity identitySnapshot;

	/**
	 * 配置绑定阶段直接写入字段时, 使旧快照失效。
	 */
	public void setId(String id) {
		this.id = id;
		this.identitySnapshot = null;
	}

	public void setDomain(String domain) {
		this.domain = domain;
		this.identitySnapshot = null;
	}

	public void setPassword(String password) {
		this.password = password;
		this.identitySnapshot = null;
	}

	public SipIdentity identitySnapshot() {
		SipIdentity snapshot = identitySnapshot;
		if (snapshot == null || !matchesSnapshot(snapshot)) {
			synchronized (this) {
				snapshot = identitySnapshot;
				if (snapshot == null || !matchesSnapshot(snapshot)) {
					snapshot = new SipIdentity(id, domain, password);
					identitySnapshot = snapshot;
				}
			}
		}
		return snapshot;
	}

	/**
	 * 以不可变快照整体替换身份三元组。
	 */
	public synchronized void applyIdentity(SipIdentity identity) {
		if (identity == null) {
			throw new IllegalArgumentException("SipIdentity must not be null");
		}
		this.id = identity.getId();
		this.domain = identity.getDomain();
		this.password = identity.getPassword();
		this.identitySnapshot = identity;
	}

	private boolean matchesSnapshot(SipIdentity snapshot) {
		return java.util.Objects.equals(snapshot.getId(), id)
				&& java.util.Objects.equals(snapshot.getDomain(), domain)
				&& java.util.Objects.equals(snapshot.getPassword(), password);
	}
}
