# Goya Security: 企业级认证授权框架

Goya Security 是一个基于 Spring Boot 3.x、Spring Security 6.x 和 Spring Authorization Server 1.x 构建的，专为现代微服务架构设计的企业级认证授权解决方案。它旨在提供一个功能完备、高度可扩展、安全可靠的安全框架，帮助开发者快速构建健壮的应用程序。

## ✨ 核心特性

- **🚀 现代化技术栈**: 完全基于 Spring Boot 3、Spring Security 6 和 Java 21+，拥抱最新技术。
- **🛡️ 完备的OAuth2支持**: 作为标准的 OAuth2/OIDC 授权服务器，支持包括客户端凭证、授权码在内的多种授权模式，并扩展支持密码模式。
- **💎 双令牌体系 (Dual-Token)**:
  - **对内服务 (Internal)**: 颁发安全性更高的**不透明令牌 (Opaque Token)**，需要资源服务器在线校验，支持令牌的实时撤销。
  - **对外服务 (External)**: 颁发性能更优的 **JWT**，资源服务器可利用公钥离线校验，极大提升系统性能与伸缩性。
- **⚡ 动态权限管理 (Dynamic RBAC)**:
  - **无硬编码**: 业务代码无需硬编码任何权限，访问控制完全基于动态的权限配置，真正实现业务与安全的解耦。
- **🧩 高度模块化与可扩展**:
  - **易于扩展**: 提供了丰富的扩展点，允许轻松自定义认证方式（如社交登录、MFA）、令牌内容、用户体系等。
- **🚀 极致性能**: 全面拥抱 Java 21 虚拟线程，以极低的资源消耗应对海量并发请求，尤其在令牌在线校验等I/O密集型场景下性能提升显著。
- **🔐 企业级账户安全**: 内置登录失败锁定、多端登录限制与会话管理、请求/响应加密等企业级安全特性。
- **🤝 Vben Admin 前端最佳实践**: 遵循 `Vben Admin` 前端框架的接口规范，提供开箱即用的后端 API 定义。

## 🏛️ 架构设计

项目采用 Maven 多模块架构，确保各部分职责清晰、易于维护和独立升级。

### 关键设计理念

#### 1. 动态权限扫描与分发

这是 Goya Security 的核心创新之一。它解决了传统 Spring Security 中权限需要硬编码或手动配置的痛点。


## 📝 API 合约 (兼容 Vben Admin)

为了与前端应用（如 Vben Admin）无缝集成，框架的示例模块将提供以下标准 API。

#### 统一响应格式
```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

#### 1. 登录接口
- **URL**: `/auth/login` (实际为 `/oauth2/token` 的密码模式封装)
- **Method**: `POST`
- **Response**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "accessToken": "eyJhbGciOiJIUzUxMiJ9..."
    }
  }
  ```

#### 2. 获取用户信息
- **URL**: `/user/info`
- **Method**: `GET`
- **Response**:
  ```json
  {
    "code": 0,
    "message": "success",
    "data": {
      "userId": "1",
      "username": "admin",
      "realName": "管理员",
      "avatar": "https://...",
      "roles": ["super", "admin"]
    }
  }
  ```

#### 3. 获取用户菜单
- **URL**: `/menus`
- **Method**: `GET`
- **Response**: 返回动态生成的菜单树形结构。
