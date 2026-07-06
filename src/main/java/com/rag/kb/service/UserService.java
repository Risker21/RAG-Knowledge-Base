package com.rag.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.kb.mapper.UserMapper;
import com.rag.kb.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public User register(String username, String password) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        userMapper.insert(user);
        return user;
    }

    public User login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        String storedPwd = user.getPassword();

        // 兼容旧版 MD5 密码：检测到 MD5 格式则走 MD5 验证，成功后升级到 BCrypt
        if (isMd5Hash(storedPwd)) {
            if (!md5(password).equals(storedPwd)) {
                throw new RuntimeException("用户名或密码错误");
            }
            // 升级为 BCrypt
            user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
            userMapper.updateById(user);
            return user;
        }

        // BCrypt 验证
        if (!BCrypt.checkpw(password, storedPwd)) {
            throw new RuntimeException("用户名或密码错误");
        }
        return user;
    }

    /** 判断是否为 MD5 的 32 位 hex 格式 */
    private boolean isMd5Hash(String pwd) {
        return pwd != null && pwd.length() == 32 && pwd.matches("[0-9a-f]{32}");
    }

    private String md5(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
