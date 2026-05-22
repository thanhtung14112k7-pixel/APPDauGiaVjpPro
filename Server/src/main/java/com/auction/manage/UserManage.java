package com.auction.manage;

import com.auction.exception.AuthErrorCode;
import com.auction.exception.AuthenticationException;
import com.auction.models.User.User;
import com.auction.enums.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class UserManage {
    private static volatile UserManage instance;

    // 🔥 SỬA ĐỒNG BỘ: Khởi tạo đúng ConcurrentHashMap để an toàn đa luồng tối đa cho mọi luồng đọc/ghi
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, String> usernameToIdMap = new ConcurrentHashMap<>();
    private final Map<String, String> emailToIdMap = new ConcurrentHashMap<>();

    private UserManage() {}

    public static UserManage getInstance() {
        UserManage temp = instance;
        if (temp == null) {
            synchronized (UserManage.class) {
                temp = instance;
                if (temp == null) {
                    temp = instance = new UserManage();
                }
            }
        }
        return temp;
    }

    /**
     * Thêm người dùng mới vào hệ thống (Khi Đăng nhập hoặc Đăng ký thành công)
     */
    public boolean addUser(User user) throws AuthenticationException {
        if (user == null) return false;

        // Kiểm tra trùng lặp thông tin an toàn qua các map phụ
        this.isUsernameExists(user.getUsername());
        this.isEmailExists(user.getEmail());

        String id = user.getId();

        // Cất vào kho RAM đồng bộ
        users.put(id, user);
        usernameToIdMap.put(user.getUsername(), id);
        emailToIdMap.put(user.getEmail(), id);

        System.out.println("[UserManage] ✅ Nạp người dùng lên RAM thành công: " + user.getUsername());
        return true;
    }

    /**
     * Cập nhật thông tin người dùng
     * 🔥 SỬA LỖI CHÍ MẠNG: Khóa cục bộ theo ID, cập nhật gián tiếp qua Setter để bảo vệ tính toàn vẹn Maps và vùng nhớ tham chiếu
     */
    public boolean updateUser(String userId, User updatedUser) throws AuthenticationException {
        if (userId == null || updatedUser == null) return false;

        this.isUserIdInvalid(userId);

        // 🔥 TỐI ƯU ĐA LUỒNG: Khóa intern ID để tránh nghẽn luồng toàn hệ thống, ai sửa người nấy xếp hàng
        synchronized (userId.intern()) {
            User oldUser = users.get(userId);

            String oldUsername = oldUser.getUsername();
            String oldEmail = oldUser.getEmail();
            String newUsername = updatedUser.getUsername();
            String newEmail = updatedUser.getEmail();

            // Validate trùng lặp nếu có sự thay đổi thông tin định danh
            if (!newUsername.equals(oldUsername)) {
                this.isUsernameExists(newUsername);
            }
            if (!newEmail.equals(oldEmail)) {
                this.isEmailExists(newEmail);
            }

            // 🔥 BẢO VỆ AN TOÀN MAPS: Chỉ cập nhật các map ánh xạ khi toàn bộ quá trình validate đã lọt qua an toàn
            if (!newUsername.equals(oldUsername)) {
                usernameToIdMap.remove(oldUsername);
                usernameToIdMap.put(newUsername, userId);
            }
            if (!newEmail.equals(oldEmail)) {
                emailToIdMap.remove(oldEmail);
                emailToIdMap.put(newEmail, userId);
            }

            // 🔥 SỬA LỖI THAM CHIẾU: Không dùng lệnh users.put(userId, updatedUser) tạo Object mới gây lệch RAM.
            // Ta cập nhật trực tiếp thông tin lên chính Object 'oldUser' đang sống.
            oldUser.setUsername(newUsername);
            oldUser.setEmail(newEmail);
            oldUser.setPassword(updatedUser.getPassword()); // Cập nhật mật khẩu nếu có
            // Nếu có các trường ví tiền, thông tin cá nhân khác, gọi Setter ở đây...

            System.out.println("[UserManage] 🔄 Cập nhật thông tin người dùng thành công: " + newUsername);
            return true;
        }
    }

    /**
     * Xóa người dùng khỏi hệ thống RAM (Gọi khi USER LOGOUT hoặc Socket Disconnect hoàn toàn)
     */
    public boolean deleteUser(String userId) throws AuthenticationException {
        if (userId == null) return false;

        this.isUserIdInvalid(userId);

        synchronized (userId.intern()) {
            User user = users.get(userId);
            String username = user.getUsername();
            String email = user.getEmail();

            // Trục xuất sạch sẽ khỏi cả 3 map để giải phóng bộ nhớ RAM triệt để
            users.remove(userId);
            usernameToIdMap.remove(username);
            emailToIdMap.remove(email);

            System.out.println("[UserManage] 🧹 Trục xuất người dùng khỏi RAM thành công: " + username);
            return true;
        }
    }

    // --- Các hàm đọc không sửa đổi: Tận dụng cơ chế đọc siêu tốc, không block luồng của ConcurrentHashMap ---

    public User getUser(String userId) {
        return userId != null ? users.get(userId) : null;
    }

    public User getUserByUsername(String username) {
        if (username == null) return null;
        String userId = usernameToIdMap.get(username);
        return userId != null ? users.get(userId) : null;
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        String userId = emailToIdMap.get(email);
        return userId != null ? users.get(userId) : null;
    }

    public User getUserById(String id) {
        return id != null ? users.get(id) : null;
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public List<User> getUsersByRole(UserRole role) {
        if (role == null) return new ArrayList<>();
        return users.values().stream()
                .filter(user -> role.name().equals(user.getRole())) // Đồng bộ so sánh theo tên enum
                .collect(Collectors.toList());
    }

    public int getUserCount() {
        return users.size();
    }

    private void isUserIdInvalid(String userId) throws AuthenticationException {
        if (userId == null || userId.isEmpty() || !users.containsKey(userId))
            throw new AuthenticationException(AuthErrorCode.USER_NOT_FOUND);
    }

    public void isUsernameExists(String username) throws AuthenticationException {
        if (username != null && usernameToIdMap.containsKey(username))
            throw new AuthenticationException(AuthErrorCode.USERNAME_ALREADY_EXISTS);
    }

    public void isEmailExists(String email) throws AuthenticationException {
        if (email != null && emailToIdMap.containsKey(email))
            throw new AuthenticationException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }
}