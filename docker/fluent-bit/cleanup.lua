function cleanup_log(tag, timestamp, record)
    -- Normalize and trim level to uppercase
    if record["level"] ~= nil then
        local lvl = tostring(record["level"])
        record["level"] = lvl:gsub("%s+", ""):upper()
    end
    
    -- Ensure uniform message field (use 'message' as standard)
    if record["message"] == nil and record["msg"] ~= nil then
        record["message"] = record["msg"]
    end
    if record["msg"] == nil and record["message"] ~= nil then
        record["msg"] = record["message"]
    end
    
    -- Normalize timestamp if missing from parser
    if record["@timestamp"] == nil and record["timestamp"] ~= nil then
        record["@timestamp"] = record["timestamp"]
    end
    
    return 2, timestamp, record
end
