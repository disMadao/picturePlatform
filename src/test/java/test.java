/**
 * @Auther: Mr. Mei
 * @Date: 2025/9/11 00:56
 * @Description:
 */

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.picturebackend.constant.UserConstant;
import com.example.picturebackend.exception.BusinessException;
import com.example.picturebackend.exception.ErrorCode;
import com.example.picturebackend.model.dto.user.UserQueryRequest;
import com.example.picturebackend.model.entity.User;
import com.example.picturebackend.model.enums.UserRoleEnum;
import com.example.picturebackend.model.vo.LoginUserVO;
import com.example.picturebackend.model.vo.UserVO;
import com.example.picturebackend.service.UserService;
import com.example.picturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

public class test {

    public static String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "mei";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    public static void main(String[] args) {
        Integer a = 1;
        System.out.println(a);
        System.out.println(getEncryptPassword("123123123"));
    }
}

