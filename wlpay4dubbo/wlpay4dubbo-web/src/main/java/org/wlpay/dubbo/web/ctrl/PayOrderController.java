package org.wlpay.dubbo.web.ctrl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.codec.Base64Encoder;
import cn.hutool.core.date.DateUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;

import java.math.BigDecimal;
import java.net.HttpCookie;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;
import org.wlpay.common.constant.PayConstant;
import org.wlpay.common.domain.RespResult;
import org.wlpay.common.util.JWTUtil;
import org.wlpay.common.util.MyLog;
import org.wlpay.common.util.MySeq;
import org.wlpay.common.util.PayDigestUtil;
import org.wlpay.common.util.RpcUtil;
import org.wlpay.common.util.XXPayUtil;
import org.wlpay.dubbo.web.service.MchAlipayService;
import org.wlpay.dubbo.web.service.MchInfoService;
import org.wlpay.dubbo.web.service.PayChannelService;
import org.wlpay.dubbo.web.service.PayOrderService;

/**
 * @Description: 支付订单,包括:统一下单,订单查询,补单等接口
 * @author dingzhiwei jmdhappy@126.com
 * @date 2017-07-05
 * @version V1.0
 * @Copyright: www.xxpay.org
 */
@Controller
public class PayOrderController {

    private final MyLog _log = MyLog.getLog(PayOrderController.class);

    @Autowired
    private PayOrderService payOrderService;

    @Autowired
    private PayChannelService payChannelService;

    @Autowired
    private MchInfoService mchInfoService;
    
    
    @Value("${secure.aes.key}")
    private String secureAesKey;
    
    /**
     * 统一下单接口:
     * 1)先验证接口参数以及签名信息
     * 2)验证通过创建支付订单
     * 3)根据商户选择渠道,调用支付服务进行下单
     * 4)返回下单数据
     * @param params
     * @return
     */
    @RequestMapping(value = "/api/pay/create_order")
    @ResponseBody
    public String payOrder(@RequestParam String params) {
        _log.info("###### 开始接收商户统一下单请求 ######");
        String logPrefix = "【商户统一下单】";
        try {
            JSONObject po = JSONObject.parseObject(params);
            JSONObject payContext = new JSONObject();
            JSONObject payOrder = null;
            // 验证参数有效性
            Object object = validateParams(po, payContext);
            if (object instanceof String) {
                _log.info("{}参数校验不通过:{}", logPrefix, object);
                return XXPayUtil.makeRetFail(XXPayUtil.makeRetMap(PayConstant.RETURN_VALUE_FAIL, object.toString(), null, null));
            }
            if (object instanceof JSONObject) payOrder = (JSONObject) object;
            if(payOrder == null) return XXPayUtil.makeRetFail(XXPayUtil.makeRetMap(PayConstant.RETURN_VALUE_FAIL, "支付中心下单失败", null, null));
            payOrder.put("expireTime", DateUtil.offsetMinute(new Date(), 5).getTime());
            int result = payOrderService.create(payOrder);
            _log.info("{}创建支付订单,结果:{}", logPrefix, result);
            if(result != 1) {
                return XXPayUtil.makeRetFail(XXPayUtil.makeRetMap(PayConstant.RETURN_VALUE_FAIL, "创建支付订单失败", null, null));
            }
            String channelId = payOrder.getString("channelId");
            switch (channelId) {
                case PayConstant.PAY_CHANNEL_WX_APP :
                    return payOrderService.doWxPayReq(PayConstant.WxConstant.TRADE_TYPE_APP, payOrder, payContext.getString("resKey"));
                case PayConstant.PAY_CHANNEL_WX_JSAPI :
                    return payOrderService.doWxPayReq(PayConstant.WxConstant.TRADE_TYPE_JSPAI, payOrder, payContext.getString("resKey"));
                case PayConstant.PAY_CHANNEL_WX_NATIVE :
                    return payOrderService.doWxPayReq(PayConstant.WxConstant.TRADE_TYPE_NATIVE, payOrder, payContext.getString("resKey"));
                case PayConstant.PAY_CHANNEL_WX_MWEB :
                    return payOrderService.doWxPayReq(PayConstant.WxConstant.TRADE_TYPE_MWEB, payOrder, payContext.getString("resKey"));
                case PayConstant.PAY_CHANNEL_ALIPAY_MOBILE :
                    return payOrderService.doAliPayReq(channelId, payOrder, payContext.getString("resKey"));
                case PayConstant.PAY_CHANNEL_ALIPAY_PC :
                    return payOrderService.doAliPayReq(channelId, payOrder, payContext.getString("resKey"));
                case PayConstant.PAY_CHANNEL_ALIPAY_WAP :
                    return payOrderService.doAliPayReq(channelId, payOrder, payContext.getString("resKey"));
                case PayConstant.PAY_CHANNEL_ALIPAY_QR :
                    return payOrderService.doAliPayReq(channelId, payOrder, payContext.getString("resKey"));
                case PayConstant.PAY_CHANNEL_ALIPAY_INDIVIDUAL:
                	return payOrderService.doAliPayReq(channelId, payOrder, payContext.getString("resKey"));
                default:
                    return XXPayUtil.makeRetFail(XXPayUtil.makeRetMap(PayConstant.RETURN_VALUE_FAIL, "不支持的支付渠道类型[channelId="+channelId+"]", null, null));
            }
        }catch (Exception e) {
            _log.error(e, "");
            return XXPayUtil.makeRetFail(XXPayUtil.makeRetMap(PayConstant.RETURN_VALUE_FAIL, "支付中心系统异常", null, null));
        }
    }
    
    @RequestMapping("pay/create_order")
    public String payOrderWeb(@RequestParam String params,Model model) {
    	String result=payOrder(params);
    	Map retMap = JSON.parseObject(result);
        if("SUCCESS".equals(retMap.get("retCode"))) {
        	model.addAttribute("payOrderId", retMap.get("payOrderId"));
        	model.addAttribute("qrCode", retMap.get("qrCode"));
        	return "redirect";
        }
        model.addAttribute("message", retMap.get("retMsg"));
    	return "error";
    }
    
    @RequestMapping("pay")
    public String pay(String o,String q,Model model,HttpServletRequest request) {
    	if(StringUtils.isBlank(o)||StringUtils.isBlank(q)) {
    		model.addAttribute("message", "系统异常");
    		return "error";
    	}
    	Enumeration<String> heads=request.getHeaderNames();
    	while(heads.hasMoreElements()) {
    		String hName=heads.nextElement();
    		_log.info("head={},value={}",hName,request.getHeader(hName));
    	}
    	_log.info("进入支付二维码页面 支付订单号 【PayOrderId】为{}", o);
    	Map<String,Object> paramMap=new HashMap<String, Object>();
    	paramMap.put("payOrderId",o);
    	_log.info("查询数据为{}", paramMap);
    	model.addAttribute("qrcode", q);
    	Map<String,Object> order=payOrderService.selectPayOrder(RpcUtil.createBaseParam(paramMap));
    	_log.info("订单数据为{}", order);
    	model.addAttribute("orderNo", order.get("mchOrderNo"));
    	model.addAttribute("payOrderId",o);
    	model.addAttribute("amount", new BigDecimal(Objects.toString( order.get("realAmount"))).movePointLeft(2).setScale(2));
    	model.addAttribute("title",order.get("subject"));
    	model.addAttribute("expireTime", DateUtil.date(Long.parseLong(Objects.toString(order.get("expireTime")))));
    	model.addAttribute("expireTimeStamp", order.get("expireTime"));
    	return "pay";
    }
    
    @RequestMapping("callpay")
    public String callpay(String t,Model model,HttpServletRequest request) {
    	String userAgent=request.getHeader("user-agent").toUpperCase();
    	if(!StringUtils.contains(userAgent, "ALIPAY")) {
    		model.addAttribute("message", "请使用支付宝APP扫码付款");
    		return "error";
    	}
    	if(StringUtils.isBlank(t)) {
    		model.addAttribute("message", "系统异常");
    		return "error";
    	}
    	String aesPayOrderId="";
    	try {
    		aesPayOrderId=JWTUtil.getParam(t);
		} catch (Exception e) {
			e.printStackTrace();
			model.addAttribute("message", "订单超时或令牌无效");
    		return "error";
		}
    	
    	String decodePayOrderId=SecureUtil.aes(secureAesKey.getBytes()).decryptStr(aesPayOrderId);
    	_log.info("解密后的订单号id为{}", decodePayOrderId);
    	Map<String, Object> paramMap=new HashMap<String, Object>();
    	paramMap.put("payOrderId",decodePayOrderId);
    	Map<String,Object> order=payOrderService.selectPayOrder(RpcUtil.createBaseParam(paramMap));
    	String expireTimeStr=Objects.toString(order.get("expireTime"));
    	String orderStatus=Objects.toString(order.get("status"));
    	_log.info("订单数据为{}", order);
    	if(StringUtils.isNotBlank(expireTimeStr)) {
    		Long expireTime=Long.parseLong(expireTimeStr);
    		if(new Date().getTime()>expireTime) {
    			model.addAttribute("message", "订单超时");
        		return "error";
    		}
    	}
    	
    	if(StringUtils.equals(orderStatus, "2")||StringUtils.equals(orderStatus, "3")) {
    		model.addAttribute("message", "不能重复支付");
        	return "error";
    	}
    	
    	String redirectUrl="alipays://platformapi/startapp?appId=20000123&actionType=scan&biz_data={\"s\": \"money\",\"u\": \""
    			+ Objects.toString(order.get("alipayPid"))
    			+"\",\"a\": \""
    			+ Objects.toString(new BigDecimal(Objects.toString(order.get("realAmount"))).movePointLeft(2))
    			+ "\",\"m\": \""
    			+ Objects.toString(order.get("subject"))
    			+ "\"}";
    	model.addAttribute("redirectUrl", redirectUrl);
    	return "realPay";
    }
    
    @RequestMapping("order/result")
    @ResponseBody
    public RespResult<Object> payResult(String o){
    	Map<String, Object> paramMap=new HashMap<String, Object>();
    	paramMap.put("payOrderId", o);
    	Map<String,Object> order=payOrderService.selectPayOrder(RpcUtil.createBaseParam(paramMap));
    	return RespResult.buildSuccessMessage(order.get("status"));
    }
    
    @RequestMapping("order/resultView")
    @ResponseBody
    public RespResult<Object> resultView(){
    	return RespResult.buildSuccessMessage("支付成功");
    }
    
    
    /**
     * 验证创建订单请求参数,参数通过返回JSONObject对象,否则返回错误文本信息
     * @param params
     * @return
     */
    private Object validateParams(JSONObject params, JSONObject payContext) {
        // 验证请求参数,参数有问题返回错误提示
        String errorMessage;
        // 支付参数
        String mchId = params.getString("mchId"); 			    // 商户ID
        String mchOrderNo = params.getString("mchOrderNo"); 	// 商户订单号
        String channelId = params.getString("channelId"); 	    // 渠道ID
        String amount = params.getString("amount"); 		    // 支付金额（单位分）
        String currency = params.getString("currency");         // 币种
        String clientIp = params.getString("clientIp");	        // 客户端IP
        String device = params.getString("device"); 	        // 设备
        String extra = params.getString("extra");		        // 特定渠道发起时额外参数
        String param1 = params.getString("param1"); 		    // 扩展参数1
        String param2 = params.getString("param2"); 		    // 扩展参数2
        String notifyUrl = params.getString("notifyUrl"); 		// 支付结果回调URL
        String sign = params.getString("sign"); 				// 签名
        String subject = params.getString("subject");	        // 商品主题
        String body = params.getString("body");	                // 商品描述信息
        // 验证请求参数有效性（必选项）
        if(StringUtils.isBlank(mchId)) {
            errorMessage = "request params[mchId] error.";
            return errorMessage;
        }
        if(StringUtils.isBlank(mchOrderNo)) {
            errorMessage = "request params[mchOrderNo] error.";
            return errorMessage;
        }
        if(StringUtils.isBlank(channelId)) {
            errorMessage = "request params[channelId] error.";
            return errorMessage;
        }
        if(!NumberUtils.isNumber(amount)) {
            errorMessage = "request params[amount] error.";
            return errorMessage;
        }
        if(StringUtils.isBlank(currency)) {
            errorMessage = "request params[currency] error.";
            return errorMessage;
        }
        if(StringUtils.isBlank(notifyUrl)) {
            errorMessage = "request params[notifyUrl] error.";
            return errorMessage;
        }
        if(StringUtils.isBlank(subject)) {
            errorMessage = "request params[subject] error.";
            return errorMessage;
        }
        if(StringUtils.isBlank(body)) {
            errorMessage = "request params[body] error.";
            return errorMessage;
        }
        // 根据不同渠道,判断extra参数
        if(PayConstant.PAY_CHANNEL_WX_JSAPI.equalsIgnoreCase(channelId)) {
            if(StringUtils.isEmpty(extra)) {
                errorMessage = "request params[extra] error.";
                return errorMessage;
            }
            JSONObject extraObject = JSON.parseObject(extra);
            String openId = extraObject.getString("openId");
            if(StringUtils.isBlank(openId)) {
                errorMessage = "request params[extra.openId] error.";
                return errorMessage;
            }
        }else if(PayConstant.PAY_CHANNEL_WX_NATIVE.equalsIgnoreCase(channelId)) {
            if(StringUtils.isEmpty(extra)) {
                errorMessage = "request params[extra] error.";
                return errorMessage;
            }
            JSONObject extraObject = JSON.parseObject(extra);
            String productId = extraObject.getString("productId");
            if(StringUtils.isBlank(productId)) {
                errorMessage = "request params[extra.productId] error.";
                return errorMessage;
            }
        }else if(PayConstant.PAY_CHANNEL_WX_MWEB.equalsIgnoreCase(channelId)) {
            if(StringUtils.isEmpty(extra)) {
                errorMessage = "request params[extra] error.";
                return errorMessage;
            }
            JSONObject extraObject = JSON.parseObject(extra);
            String productId = extraObject.getString("sceneInfo");
            if(StringUtils.isBlank(productId)) {
                errorMessage = "request params[extra.sceneInfo] error.";
                return errorMessage;
            }
            if(StringUtils.isBlank(clientIp)) {
                errorMessage = "request params[clientIp] error.";
                return errorMessage;
            }
        }

        // 签名信息
        if (StringUtils.isEmpty(sign)) {
            errorMessage = "request params[sign] error.";
            return errorMessage;
        }

        // 查询商户信息
        JSONObject mchInfo = mchInfoService.getByMchId(mchId);
        if(mchInfo == null) {
            errorMessage = "Can't found mchInfo[mchId="+mchId+"] record in db.";
            return errorMessage;
        }
        if(mchInfo.getByte("state") != 1) {
            errorMessage = "mchInfo not available [mchId="+mchId+"] record in db.";
            return errorMessage;
        }

        String reqKey = mchInfo.getString("reqKey");
        if (StringUtils.isBlank(reqKey)) {
            errorMessage = "reqKey is null[mchId="+mchId+"] record in db.";
            return errorMessage;
        }
        payContext.put("resKey", mchInfo.getString("resKey"));

        // 查询商户对应的支付渠道
        JSONObject payChannel = payChannelService.getByMchIdAndChannelId(mchId, channelId);
        if(payChannel == null) {
            errorMessage = "Can't found payChannel[channelId="+channelId+",mchId="+mchId+"] record in db.";
            return errorMessage;
        }
        if(payChannel.getByte("state") != 1) {
            errorMessage = "channel not available [channelId="+channelId+",mchId="+mchId+"]";
            return errorMessage;
        }

        // 验证签名数据
        boolean verifyFlag = XXPayUtil.verifyPaySign(params, reqKey);
        if(!verifyFlag) {
            errorMessage = "Verify XX pay sign failed.";
            return errorMessage;
        }
        // 验证参数通过,返回JSONObject对象
        JSONObject payOrder = new JSONObject();
        payOrder.put("payOrderId", MySeq.getPay());
        payOrder.put("mchId", mchId);
        payOrder.put("mchOrderNo", mchOrderNo);
        payOrder.put("channelId", channelId);
        payOrder.put("amount", Long.parseLong(amount));
        payOrder.put("currency", currency);
        payOrder.put("clientIp", clientIp);
        payOrder.put("device", device);
        payOrder.put("subject", subject);
        payOrder.put("body", body);
        payOrder.put("extra", extra);
        payOrder.put("channelMchId", payChannel.getString("channelMchId"));
        payOrder.put("param1", param1);
        payOrder.put("param2", param2);
        payOrder.put("notifyUrl", notifyUrl);
        return payOrder;
    }

}
