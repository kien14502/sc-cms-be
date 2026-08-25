package com.vnpt.mac.partner.controller;

import com.vnpt.mac.common.response.*;
import com.vnpt.mac.partner.dto.UserDtos.*;
import com.vnpt.mac.partner.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/partners/{partnerId}/users")
@Tag(name = "Partner User", description = "Danh sách và mời user vào một partner cụ thể")
public class PartnerUserController {
    private final UserService service;

    public PartnerUserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user.read') and @resourceAuth.partner(#partnerId)")
    @Operation(summary = "Danh sách user của partner", description = "Cần quyền user.read trên đúng partner sở hữu.")
    public ApiResponse<PageResponse<UserResponse>> list(@PathVariable UUID partnerId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.list(partnerId, PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping("/invitations")
    @PreAuthorize("hasAuthority('user.invite') and @resourceAuth.partner(#partnerId)")
    @Operation(summary = "Mời user mới vào partner", description = "Cần quyền user.invite. Trả về invitationToken (production nên gửi qua notification worker).")
    public ApiResponse<InvitationResponse> invite(@PathVariable UUID partnerId, @Valid @RequestBody InviteUserRequest r) {
        return ApiResponse.success(service.invite(partnerId, r));
    }
}
