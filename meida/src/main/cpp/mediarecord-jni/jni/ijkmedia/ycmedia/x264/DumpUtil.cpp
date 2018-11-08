//
// Created by kele on 2016/10/8.
//

#include "DumpUtil.h"
#include <sstream>

std::string DumpUtil::bin2hex(const char *bin, uint32_t len)
{
    std::ostringstream os;
    for(uint32_t i = 0; i<len; i++){
        char st[4];
        uint8_t c = bin[i];
        sprintf(st, "%02x ", c);
        os << st;
    }
    return os.str();
}
