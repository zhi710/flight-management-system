# -*- coding: utf-8 -*-
"""生成 4 个 JMeter 阶梯加压脚本（.jmx）。

阶梯：50 / 100 / 200 / 300 / 400 / 500 并发，每档 ramp 15s + 满负荷 120s，
6 个 ThreadGroup 顺序执行（TestPlan.serialize_threadgroups=true），采样器名带 _档位 后缀，
这样 JMeter 报告和 results.csv 都能按档位分组统计。

4 个脚本：
  01-search.jmx      前台航班搜索（读）
  02-booking.jmx     前台订票下单全链路（写：搜索→下单→查单→订单列表）
  03-admin-read.jmx  后台管理接口（读：监控大屏/航班/机组/旅客）
  04-mixed.jmx       混合场景（45% 搜索 + 25% 后台只读 + 20% 下单链路 + 10% 订单列表）
"""
import os
import xml.sax.saxutils as sx

STEPS = [int(x) for x in os.environ.get("LT_STEPS", "50,100,200,300,400,500").split(",")]
RAMP = int(os.environ.get("LT_RAMP", 15))
HOLD = int(os.environ.get("LT_HOLD", 120))   # 满负荷保持
DURATION = RAMP + HOLD

HERE = os.path.dirname(os.path.abspath(__file__))
PLANS = os.path.join(HERE, "plans")

SEARCH_QS = ("tripType=ONEWAY&departure=PEK&arrival=SHA&departDate=2026-09-15"
             "&adults=1&children=0&infants=0&cabinClass=ECONOMY")

BOOKING_BODY = (
    '{"flightId":"${flightId}","cabinClass":"${cabinClass}",'
    '"passengers":[{"name":"压测旅客","gender":"MALE","idType":"ID_CARD",'
    '"idNumber":"110101199001011234","passengerType":"ADULT"}],'
    '"contactInfo":{"name":"压测旅客","phone":"13900000001"}}'
)


def esc(s):
    return sx.escape(s, {'"': "&quot;"})


def args_props(params):
    """GET query string / POST form args"""
    out = []
    for k, v in (params or {}).items():
        out.append(
            '<elementProp name="%s" elementType="HTTPArgument">'
            '<boolProp name="HTTPArgument.always_encode">true</boolProp>'
            '<stringProp name="Argument.name">%s</stringProp>'
            '<stringProp name="Argument.value">%s</stringProp>'
            '<stringProp name="Argument.metadata">=</stringProp>'
            '<boolProp name="HTTPArgument.use_equals">true</boolProp>'
            '</elementProp>' % (esc(k), esc(k), esc(v)))
    return "".join(out)


def header_mgr(headers):
    hs = "".join(
        '<elementProp name="" elementType="Header">'
        '<stringProp name="Header.name">%s</stringProp>'
        '<stringProp name="Header.value">%s</stringProp>'
        '</elementProp>' % (esc(k), esc(v)) for k, v in headers.items())
    return ('<HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" '
            'testname="Headers" enabled="true">'
            '<collectionProp name="HeaderManager.headers">%s</collectionProp>'
            '</HeaderManager><hashTree/>' % hs)


def regex_extractor(refname, regex, default="NA"):
    return ('<RegexExtractor guiclass="RegexExtractorGui" testclass="RegexExtractor" '
            'testname="%s" enabled="true">'
            '<stringProp name="RegexExtractor.useHeaders">false</stringProp>'
            '<stringProp name="RegexExtractor.refname">%s</stringProp>'
            '<stringProp name="RegexExtractor.regex">%s</stringProp>'
            '<stringProp name="RegexExtractor.template">$1$</stringProp>'
            '<stringProp name="RegexExtractor.default">%s</stringProp>'
            '<stringProp name="RegexExtractor.match_number">1</stringProp>'
            '<boolProp name="RegexExtractor.default_empty_value">false</boolProp>'
            '</RegexExtractor><hashTree/>' % (refname, refname, esc(regex), default))


def json_extractor(refname, expr, default="NA"):
    return ('<JSONPostProcessor guiclass="JSONPostProcessorGui" testclass="JSONPostProcessor" '
            'testname="%s" enabled="true">'
            '<stringProp name="JSONPostProcessor.referenceNames">%s</stringProp>'
            '<stringProp name="JSONPostProcessor.jsonPathExprs">%s</stringProp>'
            '<stringProp name="JSONPostProcessor.match_numbers">1</stringProp>'
            '<stringProp name="JSONPostProcessor.defaultValues">%s</stringProp>'
            '</JSONPostProcessor><hashTree/>' % (refname, refname, esc(expr), default))


def sampler(name, path, method="GET", params=None, body=None, token=None,
            extractors=""):
    """一个 HTTP 采样器 + 它的 HeaderManager / 业务码提取 / 子提取器"""
    if body is not None:
        raw = ('<elementProp name="" elementType="HTTPArgument">'
               '<boolProp name="HTTPArgument.always_encode">false</boolProp>'
               '<stringProp name="Argument.value">%s</stringProp>'
               '<stringProp name="Argument.metadata">=</stringProp>'
               '</elementProp>' % esc(body))
    else:
        raw = args_props(params)
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json; charset=UTF-8"
    if token:
        headers["Authorization"] = "Bearer ${__P(%s)}" % token
    return ('<HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" '
            'testname="%s" enabled="true">'
            '<elementProp name="HTTPsampler.Arguments" elementType="Arguments" '
            'guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="Parameters" enabled="true">'
            '<collectionProp name="Arguments.arguments">%s</collectionProp></elementProp>'
            '<stringProp name="HTTPSampler.path">%s</stringProp>'
            '<stringProp name="HTTPSampler.method">%s</stringProp>'
            '<boolProp name="HTTPSampler.follow_redirects">true</boolProp>'
            '<boolProp name="HTTPSampler.auto_redirects">false</boolProp>'
            '<boolProp name="HTTPSampler.use_keepalive">true</boolProp>'
            '<boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>'
            '</HTTPSamplerProxy>'
            '<hashTree>%s%s%s</hashTree>'
            % (esc(name), raw, esc(path), method, header_mgr(headers),
               regex_extractor("bizCode", '"code"\\s*:\\s*(\\d+)'), extractors))


def simple_controller(name, children):
    return ('<GenericController guiclass="LogicControllerGui" testclass="GenericController" '
            'testname="%s" enabled="true"/><hashTree>%s</hashTree>' % (esc(name), children))


def thread_group(step, children, delay=0):
    return ('<ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="%d 并发" enabled="true">'
            '<stringProp name="ThreadGroup.on_sample_error">continue</stringProp>'
            '<elementProp name="ThreadGroup.main_controller" elementType="LoopController" '
            'guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller" enabled="true">'
            '<boolProp name="LoopController.continue_forever">true</boolProp>'
            '<stringProp name="LoopController.loops">1</stringProp></elementProp>'
            '<stringProp name="ThreadGroup.num_threads">%d</stringProp>'
            '<stringProp name="ThreadGroup.ramp_time">%d</stringProp>'
            '<boolProp name="ThreadGroup.scheduler">true</boolProp>'
            '<stringProp name="ThreadGroup.duration">%d</stringProp>'
            '<stringProp name="ThreadGroup.delay">%d</stringProp>'
            '<boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>'
            '</ThreadGroup><hashTree>%s</hashTree>'
            % (step, step, RAMP, DURATION, delay, children))


def constant_timer(ms):
    return ('<ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="think time" enabled="true">'
            '<stringProp name="ConstantTimer.delay">%d</stringProp>'
            '</ConstantTimer><hashTree/>' % ms)


def csv_data_set():
    csv_path = os.path.join(HERE, "data", "flights.csv").replace("\\", "/")
    return ('<CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="flights.csv" enabled="true">'
            '<stringProp name="filename">' + csv_path + '</stringProp>'
            '<stringProp name="fileEncoding">UTF-8</stringProp>'
            '<stringProp name="variableNames">flightId,cabinClass</stringProp>'
            '<boolProp name="ignoreFirstLine">false</boolProp>'
            '<stringProp name="delimiter">,</stringProp>'
            '<boolProp name="quotedData">false</boolProp>'
            '<boolProp name="recycle">true</boolProp>'
            '<boolProp name="stopThread">false</boolProp>'
            '<stringProp name="shareMode">shareMode.all</stringProp>'
            '</CSVDataSet><hashTree/>')


DEFAULTS = (
    '<ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement" '
    'testname="HTTP Request Defaults" enabled="true">'
    '<elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" '
    'testclass="Arguments" testname="Parameters" enabled="true">'
    '<collectionProp name="Arguments.arguments"/></elementProp>'
    '<stringProp name="HTTPSampler.domain">127.0.0.1</stringProp>'
    '<stringProp name="HTTPSampler.port">8080</stringProp>'
    '<stringProp name="HTTPSampler.protocol">http</stringProp>'
    '<stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>'
    '<stringProp name="HTTPSampler.connect_timeout">5000</stringProp>'
    '<stringProp name="HTTPSampler.response_timeout">60000</stringProp>'
    '</ConfigTestElement><hashTree/>'
)


def wrap(plan_name, body, with_csv=False):
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">\n'
            '<hashTree>\n'
            '<TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="%s" enabled="true">'
            '<stringProp name="TestPlan.comments"></stringProp>'
            '<boolProp name="TestPlan.functional_mode">false</boolProp>'
            '<boolProp name="TestPlan.serialize_threadgroups">true</boolProp>'
            '<boolProp name="TestPlan.tearDown_on_shutdown">true</boolProp>'
            '<elementProp name="TestPlan.user_defined_variables" elementType="Arguments" '
            'guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">'
            '<collectionProp name="Arguments.arguments"/></elementProp>'
            '<stringProp name="TestPlan.user_define_classpath"></stringProp>'
            '</TestPlan>\n<hashTree>\n%s%s\n%s\n</hashTree>\n</hashTree>\n</jmeterTestPlan>\n'
            % (esc(plan_name), DEFAULTS, csv_data_set() if with_csv else "", body))


# --------------------------------------------------------------- 各场景采样器

def search_sampler(step):
    return sampler("搜索航班_%d" % step, "/api/flights/search", "GET",
                   params=dict(kv.split("=", 1) for kv in SEARCH_QS.split("&")))


def booking_chain(step, with_search=True):
    parts = []
    if with_search:
        parts.append(search_sampler(step))
    parts.append(sampler("创建订单_%d" % step, "/api/orders", "POST",
                         body=BOOKING_BODY, token="MEMBER_TOKEN",
                         extractors=json_extractor("orderId", "$.data.orderId")))
    parts.append(sampler("订单详情_%d" % step, "/api/orders/${orderId}", "GET", token="MEMBER_TOKEN"))
    parts.append(sampler("订单列表_%d" % step, "/api/orders", "GET",
                         params={"page": "1", "pageSize": "10"}, token="MEMBER_TOKEN"))
    return "".join(parts)


def admin_samplers(step):
    return (sampler("后台监控大屏_%d" % step, "/api/admin/monitor/dashboard", "GET", token="ADMIN_TOKEN")
            + sampler("后台航班列表_%d" % step, "/api/admin/flights", "GET",
                      params={"page": "1", "pageSize": "20"}, token="ADMIN_TOKEN")
            + sampler("后台机组名单_%d" % step, "/api/admin/crew/list", "GET", token="ADMIN_TOKEN")
            + sampler("后台旅客查询_%d" % step, "/api/admin/passengers", "GET",
                      params={"page": "1", "pageSize": "20"}, token="ADMIN_TOKEN"))


def build_search():
    tgs = "".join(thread_group(s, constant_timer(500) + search_sampler(s)) for s in STEPS)
    return wrap("01 前台航班搜索（读）", tgs)


def build_booking():
    tgs = "".join(thread_group(s, constant_timer(1000) + booking_chain(s)) for s in STEPS)
    return wrap("02 前台订票下单全链路（写）", tgs, with_csv=True)


def build_admin():
    tgs = "".join(thread_group(s, constant_timer(500) + admin_samplers(s)) for s in STEPS)
    return wrap("03 后台管理接口（读）", tgs)


def random_controller(name, children):
    """JMeter 5.6.3 不认 ThroughputController 的 floatProp，用随机控制器按子项个数加权。"""
    return ('<RandomController guiclass="RandomControlGui" testclass="RandomController" '
            'testname="%s" enabled="true">'
            '<intProp name="InterleaveControl.style">1</intProp>'
            '</RandomController><hashTree>%s</hashTree>' % (esc(name), children))


def build_mixed():
    """比例靠子项个数加权：20 个子项 = 9 搜索(45%) + 5 后台(25%) + 4 下单链路(20%) + 2 订单列表(10%)。"""
    tgs = []
    for s in STEPS:
        # 后台拆成 4 个独立采样器 + 1 个重复项 = 5 份
        kids = ([search_sampler(s)] * 9
                + [sampler("后台监控大屏_%d" % s, "/api/admin/monitor/dashboard", "GET", token="ADMIN_TOKEN"),
                   sampler("后台航班列表_%d" % s, "/api/admin/flights", "GET", params={"page": "1", "pageSize": "20"}, token="ADMIN_TOKEN"),
                   sampler("后台机组名单_%d" % s, "/api/admin/crew/list", "GET", token="ADMIN_TOKEN"),
                   sampler("后台旅客查询_%d" % s, "/api/admin/passengers", "GET", params={"page": "1", "pageSize": "20"}, token="ADMIN_TOKEN"),
                   sampler("后台监控大屏_%d" % s, "/api/admin/monitor/dashboard", "GET", token="ADMIN_TOKEN")]
                + [simple_controller("下单链路", booking_chain(s))] * 4
                + [sampler("订单列表_%d" % s, "/api/orders", "GET", params={"page": "1", "pageSize": "10"}, token="MEMBER_TOKEN")] * 2)
        children = constant_timer(500) + random_controller("混合比例", "".join(kids))
        tgs.append(thread_group(s, children))
    return wrap("04 混合场景（读+写）", "".join(tgs), with_csv=True)


def build_smoke():
    """单线程跑一遍全部采样器，用来验证脚本本身（不出压）。"""
    tg = ('<ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="smoke" enabled="true">'
          '<stringProp name="ThreadGroup.on_sample_error">continue</stringProp>'
          '<elementProp name="ThreadGroup.main_controller" elementType="LoopController" '
          'guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller" enabled="true">'
          '<boolProp name="LoopController.continue_forever">false</boolProp>'
          '<stringProp name="LoopController.loops">1</stringProp></elementProp>'
          '<stringProp name="ThreadGroup.num_threads">1</stringProp>'
          '<stringProp name="ThreadGroup.ramp_time">1</stringProp>'
          '<boolProp name="ThreadGroup.scheduler">false</boolProp>'
          '<stringProp name="ThreadGroup.duration"></stringProp>'
          '<stringProp name="ThreadGroup.delay"></stringProp>'
          '</ThreadGroup><hashTree>%s</hashTree>'
          % (search_sampler(0) + booking_chain(0, with_search=False) + admin_samplers(0)))
    return wrap("00 冒烟（单线程各打一次）", tg, with_csv=True)


def main():
    os.makedirs(PLANS, exist_ok=True)
    plans = {
        "00-smoke.jmx": build_smoke(),
        "01-search.jmx": build_search(),
        "02-booking.jmx": build_booking(),
        "03-admin-read.jmx": build_admin(),
        "04-mixed.jmx": build_mixed(),
    }
    for name, xml in plans.items():
        path = os.path.join(PLANS, name)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(xml)
        print("%-20s %6.1f KB" % (name, len(xml.encode("utf-8")) / 1024.0))


if __name__ == "__main__":
    main()
