package com.chatApplication.user_service.service;

import com.chatApplication.user_service.dto.UpdateUserRequest;
import com.chatApplication.user_service.dto.UserResponse;
import com.chatApplication.user_service.dto.UserStatusRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService service;

    public UserController(
            UserService service){
        this.service=service;
    }

    @GetMapping("/{id}")
    public UserResponse getUser(
            @PathVariable Long id){

        return service.getUser(id);
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @RequestBody
            UpdateUserRequest request){

        return service.updateUser(
                id,
                request);
    }

    @GetMapping("/search")
    public List<UserResponse> search(
            @RequestParam String name){

        return service.searchUser(name);
    }

    @PutMapping("/{id}/status")
    public String updateStatus(
            @PathVariable Long id,
            @RequestBody
            UserStatusRequest request){

        service.updateStatus(
                id,
                request.getOnline());

        return "Status Updated";
    }
}
