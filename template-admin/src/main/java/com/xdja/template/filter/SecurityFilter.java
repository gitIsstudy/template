package com.xdja.template.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xdja.template.Constants;
import com.xdja.template.system.bean.User;

/**
 * 权限过滤器
 * @author: 周小欠
 */
public class SecurityFilter implements Filter {
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass()); 
	
	private static final String url_pref = "admin/";
	
	private static final String public_pref = "admin/public";
	
	private static final String anony_pref = "admin/anony";
	
	/**
	 * 初始化过滤器,将公共地址添加到列表中
	 */
	public void init(FilterConfig arg0) throws ServletException {
	}
	
	//根据token进行的验证
	public void doFilter(ServletRequest req, ServletResponse resp, 
			FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest)req;
		HttpServletResponse response = (HttpServletResponse)resp;
		//请求地址
		String uri = request.getRequestURI().substring(request.getContextPath().length() + 1);
		logger.debug(uri);
		//1.验证是否是公共url地址
		if(!uri.startsWith(public_pref)) {//非公共地址
			//2.验证是否有token
			String authHeader = request.getHeader("Authorization");
			if (authHeader == null || !authHeader.startsWith("Bearer ")) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			String token = authHeader.substring(7); // The part after "Bearer "
			
			//3.是否登录
			User user = null;
			try {
				Jws<Claims> claims = Jwts.parser().setSigningKey(Constants.JWT_KEY)
						.parseClaimsJws(token);
				//解析过期时间，已经过期的，按照未登陆处理。
				Date expir = claims.getBody().getExpiration();
				if(expir.getTime() - System.currentTimeMillis() <= 0) {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					return;
				}
				String id = claims.getBody().getId();
				String name = claims.getBody().getSubject();
				List<String> permission = claims.getBody().get("perm", List.class);
				user = new User();
				user.setId(Integer.parseInt(id));
				user.setName(name);
				user.setPermissions(permission);
	        } catch (Exception e) {
	        	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			
			if(user == null) {//没有登录用户
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			request.setAttribute("loginUser", user);
			//4.验证当前请求是否是登陆后不需要验证的地址
			if(!uri.startsWith(anony_pref)) {
				//不是登录后免检的地址
				//5.验证当前用户的请求是否在自身的权限列表中
				uri = uri.substring(url_pref.length(), uri.length());
				int index = uri.indexOf('/');
				uri = index != -1 ? uri.substring(0, index) : uri;
				logger.debug(uri);
				//验证用户的权限列表中有没有权限
				if(!user.getPermissions().contains(uri)) {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
			}
		}
		//公共地址放行
		chain.doFilter(request, response);
	}

	public void destroy() {
		
	}
}
