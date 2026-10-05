package com.itemll.flight_management_system_sp.controller;

import com.itemll.flight_management_system_sp.common.result.Result;
import com.itemll.flight_management_system_sp.dto.*;
import com.itemll.flight_management_system_sp.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会员中心控制器
 */
@Tag(name = "会员中心", description = "个人信息、证件管理、常用旅客、里程")
@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "获取个人信息")
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(memberService.getProfile(userId));
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody ProfileUpdateDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.updateProfile(userId, dto);
        return Result.ok();
    }

    @Operation(summary = "提交实名认证")
    @PostMapping("/realname")
    public Result<Void> submitRealname(@Valid @RequestBody RealnameDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.submitRealname(userId, dto);
        return Result.ok();
    }

    @Operation(summary = "获取证件列表")
    @GetMapping("/documents")
    public Result<List<Map<String, Object>>> getDocuments(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(memberService.getDocuments(userId));
    }

    @Operation(summary = "添加证件")
    @PostMapping("/documents")
    public Result<Void> addDocument(@RequestBody Map<String, Object> doc, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.addDocument(userId, doc);
        return Result.ok();
    }

    @Operation(summary = "更新证件")
    @PutMapping("/documents/{docId}")
    public Result<Void> updateDocument(@PathVariable Long docId, @RequestBody Map<String, Object> doc,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.updateDocument(docId, userId, doc);
        return Result.ok();
    }

    @Operation(summary = "删除证件")
    @DeleteMapping("/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long docId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.deleteDocument(docId, userId);
        return Result.ok();
    }

    @Operation(summary = "获取常用旅客列表")
    @GetMapping("/travelers")
    public Result<List<Map<String, Object>>> getTravelers(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(memberService.getTravelers(userId));
    }

    @Operation(summary = "添加常用旅客")
    @PostMapping("/travelers")
    public Result<Void> addTraveler(@RequestBody Map<String, Object> traveler, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.addTraveler(userId, traveler);
        return Result.ok();
    }

    @Operation(summary = "更新常用旅客")
    @PutMapping("/travelers/{travelerId}")
    public Result<Void> updateTraveler(@PathVariable Long travelerId, @RequestBody Map<String, Object> traveler,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.updateTraveler(travelerId, userId, traveler);
        return Result.ok();
    }

    @Operation(summary = "删除常用旅客")
    @DeleteMapping("/travelers/{travelerId}")
    public Result<Void> deleteTraveler(@PathVariable Long travelerId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.deleteTraveler(travelerId, userId);
        return Result.ok();
    }

    @Operation(summary = "里程查询")
    @GetMapping("/miles")
    public Result<Map<String, Object>> getMiles(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(memberService.getMilesInfo(userId));
    }

    @Operation(summary = "里程兑换")
    @PostMapping("/miles/redeem")
    public Result<Void> redeemMiles(@Valid @RequestBody MilesRedeemDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.redeemMiles(userId, dto.getType(), dto.getTargetId(), dto.getMiles());
        return Result.ok();
    }

    @Operation(summary = "修改密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        memberService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }
}
